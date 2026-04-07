//
// Created by lacas on 2026/2/9.
//

#ifndef DB_INF_MYSQL_INF_H
#define DB_INF_MYSQL_INF_H

#include <mariadb/mysql.h>
#include <mariadb/mariadb_rpl.h>
#include <mariadb/errmsg.h>

class Mysql_inf{
private:
    MYSQL* m_conn{nullptr};
    std::atomic<bool> m_state;
    std::vector<MYSQL_FIELD* > fd;
    std::vector<std::string> m_field;
    std::string ip = "127.0.0.1";
    uint32_t port = 3306;
    std::string user = "root";
    std::string passwd = "123456";
    MYSQL_RES* m_res;
    MYSQL_ROW* m_colum;
    std::map<uint64_t , MARIADB_RPL_EVENT*> m_table_cache;
    std::map<uint64_t ,std::string> m_table;

    struct RowValue {
        bool is_null;
        enum_field_types type;
        union {
            int32_t i32;
            int64_t i64;
            float f;
            double d;
        } num;
        std::string str;
    };
private:
    std::pair<std::string,uint32_t> get_latest_binlog();
    void conn_init();
    MARIADB_RPL* rpl_init(uint32_t server_id);
    void table_init();
public:
    Mysql_inf();
    Mysql_inf(std::string ip,uint32_t port,std::string user,std::string passwd);
    ~Mysql_inf();
    [[nodiscard]] bool is_connected() const {return m_state.load(std::memory_order_acquire);};
    bool select_database(std::string db);
    void show_all_result();
    void show_all_result_byone();
    void query(const std::string str);
    void get_binlog();
    std::vector<std::vector<std::string>> get_all_result();
};

#endif //DB_INF_MYSQL_INF_H
