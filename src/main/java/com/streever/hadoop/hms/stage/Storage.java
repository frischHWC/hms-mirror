package com.streever.hadoop.hms.stage;

import com.streever.hadoop.hms.mirror.*;
import com.streever.hadoop.hms.util.TableUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.Date;

public class Storage implements Runnable {
    private static Logger LOG = LogManager.getLogger(Storage.class);

    private Config config = null;
    private DBMirror dbMirror = null;
    private TableMirror tblMirror = null;
    private boolean successful = Boolean.FALSE;

    public boolean isSuccessful() {
        return successful;
    }

    public Storage(Config config, DBMirror dbMirror, TableMirror tblMirror) {
        this.config = config;
        this.dbMirror = dbMirror;
        this.tblMirror = tblMirror;
    }

    public static Boolean fixConfig(Config config) {
        Boolean rtn = Boolean.TRUE;
        if (config.getStorage().getStrategy() == StorageConfig.Strategy.SQL ||
                config.getStorage().getStrategy() == StorageConfig.Strategy.HYBRID) {
            // Ensure the Partitions are built right away.
            if (!config.getCluster(Environment.UPPER).getPartitionDiscovery().getInitMSCK()) {
                config.getCluster(Environment.UPPER).getPartitionDiscovery().setInitMSCK(Boolean.TRUE);
                LOG.warn("Config Property OVERRIDE: PartitionDiscovery:InitMSCK set to true to support SQL STORAGE Mapping");
            }
        }
        if (config.getStorage().getStrategy() == StorageConfig.Strategy.EXPORT_IMPORT ||
                config.getStorage().getStrategy() == StorageConfig.Strategy.HYBRID) {
            // The hcfsNamespace for each cluster needs to be set.
            // We use it to build the correct relative location in the UPPER cluster
            //   from the LOWER cluster location.
            if (config.getCluster(Environment.LOWER).getHcfsNamespace() == null ||
                    config.getCluster(Environment.UPPER).getHcfsNamespace() == null) {
                rtn = Boolean.FALSE;
            }
        }
        return rtn;
    }

    @Override
    public void run() {
        Date start = new Date();
        LOG.info("STORAGE: Migrating " + dbMirror.getDatabase() + "." + tblMirror.getName());

        // Set Database to Transfer DB.

        switch (config.getStorage().getStrategy()) {
            case SQL:
                if (!TableUtils.isACID(tblMirror.getName(), tblMirror.getTableDefinition(Environment.LOWER))) {
                    doSQL(dbMirror, tblMirror);
                } else {
                    // ACID Table support ONLY available via EXPORT_IMPORT.
                    LOG.debug("ACID Table STORAGE support only available via EXPORT/IMPORT: " +
                            dbMirror.getDatabase() + "." + tblMirror.getName());
                    successful = doExportImport(dbMirror, tblMirror);
                }
                break;
            case EXPORT_IMPORT:
                successful = doExportImport(dbMirror, tblMirror);
                break;
            case HYBRID:
                // Check the Size of the volume where the data is stored on the lower cluster.
                // Num files
                // Num of Partitions
                // Volume
                // then select
                // doSQL
                // or
                // doExportImport

                break;
            case DISTCP:
                break;
        }

        tblMirror.setPhaseSuccess(successful);

        Date end = new Date();
        LOG.info("METADATA: Migration complete for " + dbMirror.getDatabase() + "." + tblMirror.getName() + " in " +
                Long.toString(end.getTime() - start.getTime()) + "ms");
    }

    protected Boolean doSQL(DBMirror dbMirror, TableMirror tblMirror) {
        tblMirror.addAction("STORAGE", "via SQL");
        Boolean rtn = Boolean.FALSE;
        // Create table in new cluster.
        // Associate data to original cluster data.
        rtn = config.getCluster(Environment.UPPER).buildUpperSchemaUsingLowerData(config, dbMirror, tblMirror);

        // Build the Transfer table (with prefix)
        // NOTE: This will change the UPPER tabledef, so run this AFTER the buildUpperSchemaUsingLowerData
        // process
        if (rtn) {
            rtn = config.getCluster(Environment.UPPER).buildUpperTransferTable(config, dbMirror, tblMirror);
        }

        // Force the MSCK to enable the SQL transfer
        if (rtn) {
            rtn = config.getCluster(Environment.UPPER).partitionMaintenance(config, dbMirror, tblMirror, Boolean.TRUE);
        }
        // sql to move data.
        if (rtn) {
            rtn = config.getCluster(Environment.UPPER).doSqlDataTransfer(config, dbMirror, tblMirror);
        }

        // rename move table to target table.
        // TODO: Swap tables.
        if (rtn) {
            rtn = config.getCluster(Environment.UPPER).completeSqlTransfer(config, dbMirror, tblMirror);
        }

        return rtn;
    }

    protected Boolean doExportImport(DBMirror dbMirror, TableMirror tblMirror) {
        tblMirror.addAction("STORAGE", "via EXPORT/IMPORT");
        Boolean rtn = Boolean.FALSE;

        rtn = config.getCluster(Environment.LOWER).exportSchema(config, dbMirror.getDatabase(), dbMirror, tblMirror);
        if (rtn) {
            rtn = config.getCluster(Environment.UPPER).importSchemaWithData(config, dbMirror, tblMirror);
        }

        return rtn;
    }
}
