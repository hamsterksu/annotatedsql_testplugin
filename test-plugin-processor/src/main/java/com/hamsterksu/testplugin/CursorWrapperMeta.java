package com.hamsterksu.testplugin;

import org.apache.commons.lang.WordUtils;

import java.util.List;
import java.util.Map;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * Created by jbanse on 21/09/2014.
 */
public class CursorWrapperMeta {

    private final String tableCanonicalName;
    private String pkgName;

    private final String tableClassName;

    private final Map<String, String> columnToType;
    private final Map<String, String> columnToVariable;

    private final List<String> columnNameList;

    private final boolean isForView;

    public CursorWrapperMeta(Element tableClassName, List<String> columnNameList, Map<String, String> columnToType, Map<String, String> columnToVariable, boolean isForView) {
        this.columnNameList = columnNameList;
        this.columnToType = columnToType;
        this.tableClassName = tableClassName.getSimpleName().toString();
        this.tableCanonicalName = ((TypeElement) tableClassName).getQualifiedName().toString();
        this.columnToVariable = columnToVariable;
        this.isForView = isForView;
    }

    public String getPkgName() {
        return pkgName;
    }

    public String getCursorWrapperName() {
        return tableClassName.concat("Cursor");
    }

    public List<String> getColumnNameList() {
        return columnNameList;
    }

    public String getClassTypeForColumn(String columnName) {
        return columnToType.get(columnName);
    }

    public String getVariableForColumn(String columnName) {
        return columnToVariable.get(columnName);
    }

    public String getTableClassName() {
        return tableClassName;
    }

    public String getTableCanonicalName() {
        return tableCanonicalName;
    }

    public static String convertInCamelCase(String name) {
        return WordUtils.capitalizeFully(name, new char[]{'_'}).replaceAll("_", "");
    }

    public void setPkgName(String pkgName) {
        this.pkgName = pkgName;
    }

    public boolean isForView() {
        return isForView;
    }
}