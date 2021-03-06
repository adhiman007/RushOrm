package co.uk.rushorm.core.implementation;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import co.uk.rushorm.core.Rush;
import co.uk.rushorm.core.RushColumns;
import co.uk.rushorm.core.annotations.RushIgnore;
import co.uk.rushorm.core.annotations.RushList;
import co.uk.rushorm.core.exceptions.RushListAnnotationDoesNotMatchClassException;
import co.uk.rushorm.core.RushTableStatementGenerator;

/**
 * Created by stuartc on 11/12/14.
 */
public class ReflectionTableStatementGenerator implements RushTableStatementGenerator {

    private static final String TABLE_TEMPLATE = "CREATE TABLE %s (" +
            "\nid integer primary key" +
            "%s" +
            "\n);";

    private static final String JOIN_TEMPLATE = "CREATE TABLE %s (" +
            "\nid integer primary key autoincrement" +
            ",\nparent integer NOT NULL" +
            ",\nchild integer NOT NULL" +
            ",\nFOREIGN KEY (parent) REFERENCES %s(id)" +
            ",\nFOREIGN KEY (child) REFERENCES %s(id)" +
            "\n);";

    private static final String CREATE_INDEX = "CREATE INDEX %s_idx ON %s(child);";

    private List<Join> joins = new ArrayList<>();

    private class Column {
        String name;
        String type;
    }

    private class Join {
        Class key;
        Field keyField;
        Class child;
    }

    private final RushColumns rushColumns;

    public ReflectionTableStatementGenerator(RushColumns rushColumns) {
        this.rushColumns = rushColumns;
    }

    @Override
    public void generateStatements(List<Class> classes, StatementCallback statementCallback) {

        for(Class clazz : classes) {
            String sql = classToStatement(clazz);
            statementCallback.statementCreated(sql);
        }

        for(Join join : joins){
            String joinTableName = ReflectionUtils.joinTableNameForClass(join.key, join.child, join.keyField);
            String sql = joinToStatement(join, joinTableName);
            statementCallback.statementCreated(sql);
            statementCallback.statementCreated(String.format(CREATE_INDEX, joinTableName, joinTableName));
        }
    }

    private String classToStatement(Class clazz) {

        StringBuilder columnsStatement = new StringBuilder();

        List<Field> fields = new ArrayList<>();
        ReflectionUtils.getAllFields(fields, clazz);
        for (Field field : fields) {
            field.setAccessible(true);
            if(!field.isAnnotationPresent(RushIgnore.class)) {
                Column column = columnFromField(clazz, field);
                if(column != null) {
                    columnsStatement.append(",\n")
                            .append(column.name)
                            .append(" ")
                            .append(column.type);
                }
            }
        }
        return String.format(TABLE_TEMPLATE, ReflectionUtils.tableNameForClass(clazz), columnsStatement.toString());
    }

    private String joinToStatement(Join join, String joinTableName) {
        return String.format(JOIN_TEMPLATE, joinTableName,
                ReflectionUtils.tableNameForClass(join.key),
                ReflectionUtils.tableNameForClass(join.child));
    }

    private Column columnFromField(Class clazz, Field field) {

        if(Rush.class.isAssignableFrom(field.getType())){

            // One to one join table
            Join join = new Join();
            join.key = clazz;
            join.keyField = field;
            join.child = field.getType();
            joins.add(join);
            return null;

        }else if(field.isAnnotationPresent(RushList.class)) {

            // One to many join table
            RushList rushList = field.getAnnotation(RushList.class);
            Class listClass;
            try {
                listClass = Class.forName(rushList.classname());
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                throw new RushListAnnotationDoesNotMatchClassException();
            }

            if (Rush.class.isAssignableFrom(listClass)) {

                Join join = new Join();
                join.key = clazz;
                join.keyField = field;
                join.child = listClass;
                joins.add(join);
                return null;
            }

        }
        if(rushColumns.supportsField(field)) {
            Column column = new Column();
            column.name = field.getName();
            column.type = rushColumns.sqlColumnType(field);
            return column;
        }

        return null;
    }
}
