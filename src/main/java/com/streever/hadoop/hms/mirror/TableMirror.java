package com.streever.hadoop.hms.mirror;

import com.streever.hadoop.hms.util.TableUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class TableMirror {
    private DBMirror database;
    private String name;
    private boolean overwrite = Boolean.FALSE;
    private boolean transitionCreated = Boolean.FALSE;
    private boolean exportCreated = Boolean.FALSE;
    private boolean existingTableDropped = Boolean.FALSE;
    private boolean imported = Boolean.FALSE;
    private boolean locationAdjusted = Boolean.FALSE;
    private boolean discoverPartitions = Boolean.FALSE;
    private List<String> issues = new ArrayList<String>();
    private List<String> propAdd = new ArrayList<String>();

    public String getName() {
        return name;
    }

    public TableMirror(DBMirror database, String tablename) {
        this.database = database;
        this.name = tablename;
    }

    public boolean isThereAnIssue() {
        return issues.size() > 0 ? Boolean.TRUE : Boolean.FALSE;
    }

    public boolean whereTherePropsAdded() {
        return propAdd.size() > 0 ? Boolean.TRUE : Boolean.FALSE;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    public boolean isDiscoverPartitions() {
        return discoverPartitions;
    }

    public void setDiscoverPartitions(boolean discoverPartitions) {
        this.discoverPartitions = discoverPartitions;
    }

    public boolean isLocationAdjusted() {
        return locationAdjusted;
    }

    public void setLocationAdjusted(boolean locationAdjusted) {
        this.locationAdjusted = locationAdjusted;
    }

    public boolean isTransitionCreated() {
        return transitionCreated;
    }

    public void setTransitionCreated(boolean transitionCreated) {
        this.transitionCreated = transitionCreated;
    }

    public boolean isExportCreated() {
        return exportCreated;
    }

    public void setExportCreated(boolean exportCreated) {
        this.exportCreated = exportCreated;
    }

    public boolean isExistingTableDropped() {
        return existingTableDropped;
    }

    public void setExistingTableDropped(boolean existingTableDropped) {
        this.existingTableDropped = existingTableDropped;
    }

    public boolean isImported() {
        return imported;
    }

    public void setImported(boolean imported) {
        this.imported = imported;
    }

    public List<String> getIssues() {
        return issues;
    }

    public List<String> getPropAdd() {
        return propAdd;
    }

    public void addIssue(String issue) {
        getIssues().add(issue);
    }

    public void addProp(String propAdd) {
        getPropAdd().add(propAdd);
    }

    // There are two environments (UPPER and LOWER)
    private Map<Environment, List<String>> tableDefinitions = new TreeMap<Environment, List<String>>();
    private Map<Environment, Boolean> tablePartitioned = new TreeMap<Environment, Boolean>();
    private Map<Environment, List<String>> tablePartitions = new TreeMap<Environment, List<String>>();

    // TODO: Partitions
    public void setPartitioned(Environment environment, Boolean partitioned) {
        tablePartitioned.put(environment, partitioned);
    }

    private Boolean isPartitioned(Environment environment) {
        Boolean rtn = tablePartitioned.get(environment);
        if (rtn != null) {
            return rtn;
        } else {
            return Boolean.FALSE;
        }
    }

    public List<String> getTableDefinition(Environment environment) {
        return tableDefinitions.get(environment);
    }

    public void setTableDefinition(Environment environment, List<String> tableDefList) {
        if (tableDefinitions.containsKey(environment)) {
            tableDefinitions.replace(environment, tableDefList);
        } else {
            tableDefinitions.put(environment, tableDefList);
        }
    }

    public List<String> getPartitionDefinition(Environment environment) {
        return tablePartitions.get(environment);
    }

    public void setPartitionDefinition(Environment environment, List<String> tablePartitionList) {
        if (tablePartitions.containsKey(environment)) {
            tablePartitions.replace(environment, tablePartitionList);
        } else {
            tablePartitions.put(environment, tablePartitionList);
        }
    }

    /*
    Build upper environment table def from lower environment definition.
     */
    public void translate(String tableName, Cluster from, Cluster to) {
        List<String> tblDef = null;

        // Get Lower and convert to Upper
        List<String> fromTblDef = tableDefinitions.get(from);
        if (fromTblDef != null) {
            List<String> toTblDef = new ArrayList<String>(fromTblDef);
            tableDefinitions.put(to.getEnvironment(), toTblDef);
            if (TableUtils.isLegacyManaged(from, tableName, fromTblDef)) {

            }
//            TableUtils.updateTblProperty("numRows", "0", toTblDef);
            // CREATE TABLE to CREATE EXTERNAL TABLE
            // Add 'IF NOT EXISTS'?
            // When CREATE TABLE ...  add 'purge'?  For Stage 2, yes.  For Stage 1, add marker to tblproperties
            //    'legacy.managed'
            // Add tbl property. 'hive.mirror'='stage-1'
            // Change LOCATION from old Namespace to NEW namespace.
            // Remove 'COLUMN_STATS_ACCURATE' from tbl properties.
            TableUtils.removeTblProperty("COLUMN_STATS_ACCURATE", toTblDef);
            // Remove 'numFiles'
            TableUtils.removeTblProperty("numFiles", toTblDef);
            // Remove 'numRows'
            TableUtils.removeTblProperty("numRows", toTblDef);
            // Remove 'rawDataSize'
            TableUtils.removeTblProperty("rawDataSize", toTblDef);
            // Remove 'totalSize'
            TableUtils.removeTblProperty("totalSize", toTblDef);
            // Remove 'transient_lastDdlTime'
            TableUtils.removeTblProperty("transient_lastDdlTime", toTblDef);
            // If ORC, is there a rule to set bucket version or something...?

            int locIdx = toTblDef.indexOf("LOCATION");
            String location = toTblDef.get(locIdx + 1);
//            int tProps = toTblDef.indexOf("TBLPROPERTIES (");
//            toTblDef.add(tProps + 1, " 'external.table.purge'='true'");
            System.out.println(toTblDef.toString());
        } else {
            // No table def found.
        }

    }

}
