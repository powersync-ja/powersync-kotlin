#ifndef POWER_SYNC_SQLITE_H_INCLUDED
#define POWER_SYNC_SQLITE_H_INCLUDED

#ifdef __cplusplus
extern "C" {
#endif

int sqlite3_powersync_init(sqlite3 *db, char **pzErrMsg,
                           const sqlite3_api_routines *pApi);


#ifdef __cplusplus
}
#endif

int init_powersync_sqlite_plugin();

#endif