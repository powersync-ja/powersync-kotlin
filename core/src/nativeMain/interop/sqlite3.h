// A subset of sqlite3.h that only includes the symbols this Kotlin package needs.
#include <stdint.h>

typedef struct sqlite3 sqlite3;
typedef struct sqlite3_stmt sqlite3_stmt;

int sqlite3_initialize();

int sqlite3_open_v2(char *filename, sqlite3 **ppDb, int flags,
        char *zVfs);
int sqlite3_close_v2(sqlite3 *db);

// Error handling
int sqlite3_extended_result_codes(sqlite3 *db, int onoff);
int sqlite3_extended_errcode(sqlite3 *db);
char *sqlite3_errmsg(sqlite3 *db);
char *sqlite3_errstr(int code);
int sqlite3_error_offset(sqlite3 *db);
void sqlite3_free(void *ptr);

// Versions
char *sqlite3_libversion();
char *sqlite3_sourceid();
int sqlite3_libversion_number();

// Database
int sqlite3_get_autocommit(sqlite3 *db);
int sqlite3_db_config(sqlite3 *db, int op, ...);
int sqlite3_load_extension(
        sqlite3 *db,          /* Load the extension into this database connection */
        const char *zFile,    /* Name of the shared library containing extension */
        const char *zProc,    /* Entry point.  Derived from zFile if 0 */
        char **pzErrMsg       /* Put error message here if not 0 */
);

// Statements
int sqlite3_prepare_v3(sqlite3 *db, const char *zSql, int nByte,
        unsigned int prepFlags, sqlite3_stmt **ppStmt,
        const char **pzTail);
int sqlite3_finalize(sqlite3_stmt *pStmt);
int sqlite3_step(sqlite3_stmt *pStmt);
int sqlite3_reset(sqlite3_stmt *pStmt);
int sqlite3_clear_bindings(sqlite3_stmt*);

int sqlite3_column_count(sqlite3_stmt *pStmt);
int sqlite3_bind_parameter_count(sqlite3_stmt *pStmt);
char *sqlite3_column_name(sqlite3_stmt *pStmt, int N);

int sqlite3_bind_blob64(sqlite3_stmt *pStmt, int index, void *data,
        uint64_t length, void *destructor);
int sqlite3_bind_double(sqlite3_stmt *pStmt, int index, double data);
int sqlite3_bind_int64(sqlite3_stmt *pStmt, int index, int64_t data);
int sqlite3_bind_null(sqlite3_stmt *pStmt, int index);
int sqlite3_bind_text16(sqlite3_stmt *pStmt, int index, char *data,
        int length, void *destructor);

void *sqlite3_column_blob(sqlite3_stmt *pStmt, int iCol);
double sqlite3_column_double(sqlite3_stmt *pStmt, int iCol);
int64_t sqlite3_column_int64(sqlite3_stmt *pStmt, int iCol);
char *sqlite3_column_text(sqlite3_stmt *pStmt, int iCol);
int sqlite3_column_bytes(sqlite3_stmt *pStmt, int iCol);
int sqlite3_column_bytes16(sqlite3_stmt *pStmt, int iCol);
int sqlite3_column_type(sqlite3_stmt *pStmt, int iCol);
