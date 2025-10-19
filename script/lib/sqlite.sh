# Check if sqlite3 binary exists
check_sqlite() {
    if [ -x "/system/xbin/sqlite3" ]; then
        SQLITE3="/system/xbin/sqlite3"
    elif [ -x "/system/bin/sqlite3" ]; then
        SQLITE3="/system/bin/sqlite3"
    elif [ -x "/data/local/tmp/sqlite3" ]; then
        SQLITE3="/data/local/tmp/sqlite3"
    else
        log_msg "Error: sqlite3 not found in the expected directories."
        exit 1
    fi
}

# Optimize SQLite databases for performance
optimize_db_performance() {
    check_sqlite
    for db_file in $(busybox find $1 -iname "*.db"); do
        log_msg "Optimizing performance for database: $db_file"
        $SQLITE3 "$db_file" 'PRAGMA synchronous = OFF;'
        $SQLITE3 "$db_file" 'PRAGMA journal_mode = WAL;'
        $SQLITE3 "$db_file" 'PRAGMA cache_size = 10000;'
        $SQLITE3 "$db_file" 'REINDEX;'
    done
}

# Optimize SQLite databases for balance
optimize_db_balance() {
    check_sqlite
    for db_file in $(busybox find $1 -iname "*.db"); do
        log_msg "Optimizing balance for database: $db_file"
        $SQLITE3 "$db_file" 'PRAGMA synchronous = NORMAL;'
        $SQLITE3 "$db_file" 'PRAGMA journal_mode = WAL;'
        $SQLITE3 "$db_file" 'PRAGMA cache_size = 5000;'
        $SQLITE3 "$db_file" 'VACUUM;'
        $SQLITE3 "$db_file" 'REINDEX;'
    done
}

# Main optimization function
sql_opt() {
    directories=("/data" "/dbdata" "/datadata" "/sdcard")
    for dir in "${directories[@]}"; do
        if [ -d "$dir" ]; then
            log_msg "Starting SQLite optimization in directory: $dir"
            case "$1" in
                performance)
                    optimize_db_performance "$dir"
                    ;;
                balance)
                    optimize_db_balance "$dir"
                    ;;
                *)
                    log_msg "Unknown optimization mode. Defaulting to 'performance'."
                    optimize_db_performance "$dir"
                    ;;
            esac
        else
            log_msg "Directory $dir does not exist or is not accessible."
        fi
    done
}

# Help function
help_sqlite() {
    echo "Usage: Lxcore -sqlite [performance|balance|help]"
    echo "Options:"
    echo "  performance   Optimize SQLite databases for maximum performance."
    echo "  balance       Optimize SQLite databases for a balance between performance and reliability."
    echo "  help          Show this help message."
}

# Main entry point for SQLite commands
main_sqlite() {
    case "$1" in
        performance)
            sql_opt "performance"
            ;;
        balance)
            sql_opt "balance"
            ;;
        help)
            help_sqlite
            ;;
        *)
            echo "Unknown option. Use 'Lxcore -sqlite help' for usage."
            exit 1
            ;;
    esac
}