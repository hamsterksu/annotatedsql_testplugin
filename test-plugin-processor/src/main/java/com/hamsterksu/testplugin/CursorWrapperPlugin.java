package com.hamsterksu.testplugin;

import com.annotatedsql.annotation.sql.Column;
import com.annotatedsql.ftl.ColumnMeta;
import com.annotatedsql.ftl.SchemaMeta;
import com.annotatedsql.ftl.TableColumns;
import com.annotatedsql.ftl.ViewMeta;
import com.annotatedsql.processor.ProcessorLogger;
import com.annotatedsql.processor.sql.TableResult;
import com.annotatedsql.util.ClassUtil;
import com.hamsterksu.testplugin.annotation.CursorType;
import com.hamsterksu.testplugin.annotation.CursorWrapper;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

/**
 * Created by hamsterksu on 04.10.2014.
 */
public class CursorWrapperPlugin implements com.annotatedsql.processor.sql.ISchemaPlugin {

    private ProcessorLogger logger;
    private ProcessingEnvironment processingEnv;
    private Configuration cfg = new Configuration();
    private List<CursorWrapperMeta> wrappers = new ArrayList<CursorWrapperMeta>();
    private LinkedHashMap<String, Map<String, String>> javaTypesForTables = new LinkedHashMap<String, Map<String, String>>();

    @Override
    public void init(ProcessingEnvironment processingEnv, ProcessorLogger logger) {
        this.processingEnv = processingEnv;
        this.logger = logger;
        this.logger.i("[CursorWrapper] init");
        cfg.setTemplateLoader(new ClassTemplateLoader(this.getClass(), "/res"));
        this.logger.i("[CursorWrapper] inited");
    }

    @Override
    public void processTable(TypeElement element, TableResult tableInfo) {
        this.logger.i("[CursorWrapper] processTable");
        this.logger.i("[CursorWrapper] e.name = " + element.getSimpleName());
        CursorWrapper wrapper = element.getAnnotation(CursorWrapper.class);
        if (wrapper == null) {
            this.logger.i("[CursorWrapper] can't find @CursorWrapper. ignore table " + tableInfo.getTableName());
            return;
        }
        TableColumns tableColumns = tableInfo.getTableColumns();

        Map<String, String> javaTypes = findColumnsTypes(element);
        for (String c : tableColumns) {
            if (!javaTypes.containsKey(c)) {
                logger.i("add types : column " + c + "=> type " + getDefaultJavaType(tableColumns.getSqlType(c), tableColumns.isColumnNotNull(c)));
                javaTypes.put(c, getDefaultJavaType(tableColumns.getSqlType(c), tableColumns.isColumnNotNull(c)));
            }
        }
        javaTypesForTables.put(tableInfo.getTableName(), javaTypes);
        logger.i("add types for table: " + tableInfo.getTableName() + ", columns : " + Arrays.toString(javaTypes.keySet().toArray()));
        CursorWrapperMeta lCursorWrapperMeta = new CursorWrapperMeta(element,
                tableColumns.toColumnsList(),
                javaTypes,
                tableColumns.getColumn2Variable(), false);

        wrappers.add(lCursorWrapperMeta);
        this.logger.i("[CursorWrapper] processTable end");
    }

    private Map<String, String> findColumnsTypes(TypeElement element) {
        Map<String, String> result = new HashMap<String, String>();

        List<Element> fields = ClassUtil.getAllClassFields(element);
        for (Element f : fields) {
            if (!(f instanceof VariableElement)) {
                continue;
            }
            CursorType column = f.getAnnotation(CursorType.class);
            if (column == null) {
                continue;
            }
            String javaType = getJavaType(processingEnv, column);
            String columnName = (String) ((VariableElement) f).getConstantValue();
            result.put(columnName, javaType);
        }
        return result;
    }

    private String getJavaType(ProcessingEnvironment env, CursorType column) {
        TypeMirror annotationClassField = null;
        try {
            column.value();
        } catch (MirroredTypeException e) {
            annotationClassField = e.getTypeMirror();
        }
        String declaringType;
        if (annotationClassField.getKind() == TypeKind.DECLARED) {
            declaringType = env.getTypeUtils().asElement(annotationClassField).getSimpleName().toString();
        } else {
            declaringType = annotationClassField.toString();
        }
        return declaringType;
    }

    @Override
    public void processView(Element element, ViewMeta meta) {
        this.logger.i("[CursorWrapper] processView");
        CursorWrapper wrapper = element.getAnnotation(CursorWrapper.class);
        if (wrapper != null) {
            List<String> columnNameList = new ArrayList<String>();
            Map<String, String> columnToVariable = new HashMap<String, String>();
            Map<String, String> columnToType = new HashMap<String, String>();
            for (ViewMeta.ViewTableInfo lViewTableInfo : meta.getTables()) {
                for (ColumnMeta lColumnMeta : lViewTableInfo.getColumns()) {
                    //get base column name to get type.
                    final String[] fullName = lColumnMeta.fullName.split("\\.");
                    final String baseColumnName = fullName[fullName.length - 1];

                    columnNameList.add(lColumnMeta.alias);
                    columnToVariable.put(lColumnMeta.alias, lColumnMeta.variableAlias);
                    columnToType.put(lColumnMeta.alias, javaTypesForTables.get(lViewTableInfo.getTableName()).get(baseColumnName));
                }
            }
            wrappers.add(new CursorWrapperMeta(element,
                    columnNameList,
                    columnToType,
                    columnToVariable, true));
        } else {
            this.logger.i("[CursorWrapper] can't find @CursorWrapper. ignore view " + meta.getViewName());
        }
        this.logger.i("[CursorWrapper] processView end");
    }

    @Override
    public void processRawQuery(Element element, ViewMeta meta) {
        this.logger.i("[CursorWrapper] processRawQuery");
        this.logger.i("[CursorWrapper] processRawQuery end");
    }

    @Override
    public void processSchema(Element element, SchemaMeta schema) {
        this.logger.i("[CursorWrapper] processSchema");
        processAbstractCursorWrapper(schema);
        for (CursorWrapperMeta wrapper : wrappers) {
            wrapper.setPkgName(schema.getPkgName());
            if (wrapper.isForView()) {
                processCursorWrapperForView(wrapper);
            } else {
                processCursorWrapper(wrapper);
            }
        }
        this.logger.i("[CursorWrapper] processSchema end");
    }

    private void processAbstractCursorWrapper(SchemaMeta model) {
        this.logger.i("[CursorWrapper] processAbstractCursorWrapper");
        JavaFileObject file;
        try {
            String className = model.getPkgName() + ".AbstractCursorWrapper";
            file = processingEnv.getFiler().createSourceFile(className);
            logger.i("[CursorWrapper] processAbstractCursorWrapper: Creating file:  " + className + " in " + file.getName());
            Writer out = file.openWriter();
            Template t = cfg.getTemplate("abstractcursorwrapper.ftl");
            t.process(model, out);
            out.flush();
            out.close();
        } catch (IOException e) {
            logger.e("[CursorWrapper] EntityProcessor IOException: ", e);
        } catch (TemplateException e) {
            logger.e("[CursorWrapper] EntityProcessor TemplateException: ", e);
        }
        this.logger.i("[CursorWrapper] processAbstractCursorWrapper end");
    }

    private void processCursorWrapper(CursorWrapperMeta tableMeta) {
        this.logger.i("[CursorWrapper] processCursorWrapper");
        JavaFileObject file;
        try {
            String className = tableMeta.getPkgName() + "." + tableMeta.getCursorWrapperName();
            file = processingEnv.getFiler().createSourceFile(className);
            logger.i("[CursorWrapper] Creating file:  " + className + " in " + file.getName());
            Writer out = file.openWriter();
            Template t = cfg.getTemplate("cursor_wrapper.ftl");
            t.process(tableMeta, out);
            out.flush();
            out.close();
        } catch (IOException e) {
            logger.e("EntityProcessor IOException: ", e);
        } catch (TemplateException e) {
            logger.e("EntityProcessor TemplateException: ", e);
        }
        this.logger.i("[CursorWrapper] processCursorWrapper end");
    }

    private void processCursorWrapperForView(CursorWrapperMeta tableMeta) {
        this.logger.i("[CursorWrapperForView] processCursorWrapper");
        JavaFileObject file;
        try {
            file = processingEnv.getFiler().createSourceFile(tableMeta.getPkgName() + "." + tableMeta.getCursorWrapperName());
            logger.i("[CursorWrapperForView] Creating file:  " + tableMeta.getPkgName() + "." + tableMeta.getCursorWrapperName());
            Writer out = file.openWriter();
            Template t = cfg.getTemplate("view_cursor_wrapper.ftl");
            t.process(tableMeta, out);
            out.flush();
            out.close();
        } catch (IOException e) {
            logger.e("[CursorWrapperForView] EntityProcessor IOException: ", e);
        } catch (TemplateException e) {
            logger.e("[CursorWrapperForView] EntityProcessor TemplateException: ", e);
        }
    }

    private String getDefaultJavaType(Column.Type sqlType, boolean isNotNull) {
        switch (sqlType) {
            case BLOB:
                return byte[].class.getSimpleName();
            case INTEGER:
                if (isNotNull) {
                    return int.class.getSimpleName();
                } else {
                    return Integer.class.getSimpleName();
                }
            case REAL:
                if (isNotNull) {
                    return double.class.getSimpleName();
                } else {
                    return Double.class.getSimpleName();
                }
            default:
                return String.class.getSimpleName();
        }
    }
}
