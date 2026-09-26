# optimize SQLite database for performance
optimize_db_performance() {
    for db_file in $(busybox find $1 -iname "*.db"); do
        $SQLITE3 "$db_file" 'PRAGMA synchronous = OFF;'
        $SQLITE3 "$db_file" 'PRAGMA journal_mode = WAL;'
        $SQLITE3 "$db_file" 'PRAGMA cache_size = 10000;'
        $SQLITE3 "$db_file" 'REINDEX;'
    done
}

# optimize SQLite database for balance
optimize_db_balance() {
    for db_file in $(busybox find $1 -iname "*.db"); do
        $SQLITE3 "$db_file" 'PRAGMA synchronous = NORMAL;'
        $SQLITE3 "$db_file" 'PRAGMA journal_mode = WAL;'
        $SQLITE3 "$db_file" 'PRAGMA cache_size = 5000;'
        $SQLITE3 "$db_file" 'VACUUM;'
        $SQLITE3 "$db_file" 'REINDEX;'
    done
}

sql_opt() {
    directories=("/data" "/dbdata" "/datadata" "/sdcard")
    for dir in "${directories[@]}"; do
        if [ -d "$dir" ]; then
            case "$1" in
                performance)
                    optimize_db_performance "$dir"
                    ;;
                balance)
                    optimize_db_balance "$dir"
                    ;;
                *)
                    optimize_db_performance "$dir"
                    ;;
            esac
        fi
    done
}