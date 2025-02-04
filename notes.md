# Notes

The application is built against CDP 7.1 binaries.

## Issue 1

Connecting to an HDP cluster running 2.6.5 with Binary protocol and Kerberos triggers an incompatibility issue:
`Unrecognized Hadoop major version number: 3.1.1.7.1....`

### Solution

The application is built with CDP libraries (excluding the Hive Standalone Driver).  When `kerberos` is the `auth` protocol to connect to **Hive 1**, it will get the application libs which will NOT be compatible with the older cluster.

Kerberos connections are only supported to the CDP cluster.  

When connecting via `kerberos`, you will need to include the `--hadoop-classpath` when launching the application.     

## Features

- Add PARTITION handling
- Add Stages
- Add Check for existing table on upper cluster.
    - option for override def in stage 1.
    
    
When the "EXTERNAL" tables are added to the RIGHT cluster, they're added with "discover.partitions"="true".  But they
don't appear to get fixed in CDP.  https://cwiki.apache.org/confluence/display/Hive/LanguageManual+DDL#LanguageManualDDL-DiscoverPartitions

This appears to be a feature in Hive 4.0.  Need to see about backport to CDP.
When Hive Metastore Service (HMS) is started in remote service mode, a background thread (PartitionManagementTask) gets scheduled periodically every 300s (configurable via metastore.partition.management.task.frequency config) that looks for tables with "discover.partitions" table property set to true and performs msck repair in sync mode. If the table is a transactional table, then Exclusive Lock is obtained for that table before performing msck repair. With this table property, "MSCK REPAIR TABLE table_name SYNC PARTITIONS" is no longer required to be run manually.


## Issue 2

Caused by: java.lang.RuntimeException: org.apache.hadoop.hive.ql.security.authorization.plugin.HiveAccessControlException:Permission denied: user [dstreev] does not have [ALL] privilege on [hdfs://HDP50/apps/hive/warehouse/tpcds_bin_partitioned_orc_10.db/web_site]

Checked permissions of 'dstreev': Found that the 'dstreev' user was NOT the owner of the files in these directories. The user running the process needs to be in 'dfs.permissions.superusergroup' for the lower clusters 'hdfs' service.  Ambari 2.6 has issues setting this property: https://jira.cloudera.com/browse/EAR-7805

Follow workaround above or add user to the 'hdfs' group. Or use Ranger to allow all access. On my cluster, with no Ranger, I had to use '/var/lib/ambari-server/resources/scripts/configs.py' to set it manually for Ambari.

`sudo ./configs.py --host=k01.streever.local --port=8080 -u admin -p admin -n hdp50 -c hdfs-site -a set -k dfs.permissions.superusergroup -v hdfs_admin`


## Tip 1 (not validated)

Increase the 'hive_on_tez' `hive.msck.repair.batch.size` to improve partition discovery times.

HMS:
`hive.exec.input.listing.max.threads`
`hive.load.dynamic.partitions.thread`
`hive.metastore.fshandler.threads` (seemed to be the only one the cm noticed on restart)

## Tip 2

It seems that when there are ".hive-staging..." files in a table directory that is having partitions build for either by MSCK or ALTER SET LOCATION, it REALLY slows down the process.

## Tip 3

Reruns on External Tables without (purge) need to use hadoopcli to remove directory.  Need this to be a setting.

What to do about external tables where the data doesn't all reside under the table 'location'?

## Tip 4

Partition Creation in the STORAGE stage is expensive.  The METADATA tasks are a lot, but the real constraints come from the hive MoveTask that happens for each Partition as it's created.  This burden falls on the HS2 server to manage this process and will lead to significant performance impacts on that server.

Separate HS2 and Metastore Services should be setup to handle this when transfers will include numerous partitions.

## Idea

Tip 4, would distcp of parent table directory and partition discovery speed this process up?  I think it would.

## Tip 5

Take snapshots of areas you'll touch:
- The HMS database on the LEFT and RIGHT clusters
- A snapshot of the HDFS directories on BOTH the LEFT and RIGHT clusters that will be used/touched.


----
- Need to document concepts on Permissions when doing this.
- For distcp, we can issue an external 'hadoop distcp' call, retrieve the application id and use a background thread to watch for the completion of the job via the YARN REST API.


## Skip dir permission auth/check for create and alter table locations
Add this to the HS2 instance on the RIGHT cluster, when Ranger is used for Auth.
This skips the check done against every directory at the table location (for CREATE or ALTER LOCATION).  Allowing the process of CREATE/ALTER to run much faster.

`ranger.plugin.hive.urlauth.filesystem.schemes=file`

Recommend to turn this back after the migration is complete.  This setting exposes permissions issues at the time of CREATE/ALTER.  So by skipping this, future access issues may arise if the permissions aren't aligned, which isn't a Ranger/Hive issue, it's a permissions issue.
