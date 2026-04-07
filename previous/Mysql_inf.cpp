//
// Created by lacas on 2026/2/9.
//

#define WRITE_ROWS_EVENT_V2  30
#define UPDATE_ROWS_EVENT_V2 31
#define DELETE_ROWS_EVENT_V2 32

#include <csignal>
#include "Mysql_inf.h"

using namespace std;

Mysql_inf::Mysql_inf():m_state(false),m_colum(nullptr),m_res(nullptr) {
    conn_init();
}

Mysql_inf::~Mysql_inf() {
    if(m_res) {
        mysql_free_result(m_res);
        m_res = nullptr;
        m_state.store(false,std::memory_order_release);
    }
    for(auto& elem : m_table_cache) {
        MARIADB_RPL_EVENT *e = elem.second;
        auto &tm = e->event.table_map;
        if (tm.database.str) delete[] tm.database.str;
        if (tm.table.str) delete[] tm.table.str;
        if (tm.column_types.str) delete[] tm.column_types.str;
        if(tm.metadata.str) delete[] tm.metadata.str;
        delete e;
    }
    mysql_close(m_conn);
}

void Mysql_inf::show_all_result() {
    m_res = mysql_store_result(m_conn);
    unsigned int field = 0;
    if(m_res)
        field = mysql_num_fields(m_res);
    else return;
    MYSQL_ROW row;
    while((row = mysql_fetch_row(m_res))){
        for(int i = 0; i < field; i++)
            cout << row[i] << '\t';
        cout << '\n';
    }
    mysql_free_result(m_res);
    m_res = nullptr;
}

void Mysql_inf::query(const std::string str) {
    if(m_res){
        cout << "m_res hasn't release!\n";
        return;
    }
    if(mysql_query(m_conn,str.c_str()) != 0){
        cout << "Query error: " << mysql_error(m_conn) << "\n";
        cout << "Error code: " << mysql_errno(m_conn) << "\n";
    }
}

void Mysql_inf::show_all_result_byone() {
    m_res = mysql_use_result(m_conn);
    MYSQL_ROW  row;
    unsigned int field = 0;
    if(m_res)
        field = mysql_num_fields(m_res);
    else return;
    while((row = mysql_fetch_row(m_res))){
        for(int i = 0;i < field; i++)
            cout << row[i] << '\t';
        cout << '\n';
    }
    mysql_free_result(m_res);
    m_res = nullptr;
}

bool Mysql_inf::select_database(std::string db) {
    if(!mysql_select_db(m_conn,db.c_str()))
        return true;
    return false;
}

Mysql_inf::Mysql_inf(std::string ip,uint32_t port,std::string user,std::string passwd):
m_state(false),m_colum(nullptr),m_res(nullptr),ip(std::move(ip)),port(port),user(std::move(user)),passwd(std::move(passwd)) {
    conn_init();
}

void Mysql_inf::get_binlog() {
    MARIADB_RPL* rpl = rpl_init(999);
    MARIADB_RPL_EVENT *new_event = nullptr;
    while (1) {
        new_event = mariadb_rpl_fetch(rpl, new_event);
        if (!new_event) {
            if (rpl->error_no)
                cout << "Fetch error [" << rpl->error_no << "]: " << rpl->error_msg << '\n';
            else cout << "Connect close\n";
            cout.flush();
            break;
        }
//        if (!new_event->ok && new_event->event_type != ROTATE_EVENT) {
//            cout << "Event checksum error, skipping\n";
//            mariadb_free_rpl_event(new_event);
//            new_event = nullptr;
//            continue;
//        }

        int evt = (int)new_event->event_type;

        if (evt == ROTATE_EVENT) {
            auto &rotate = new_event->event.rotate;
            std::string new_file(rotate.filename.str, rotate.filename.length);
            if (new_file != rpl->filename) {
                cout << "ROTATE to: " << new_file << " pos=" << rotate.position << '\n';
                cout.flush();
                free(rpl->filename);
                rpl->filename = _strdup(new_file.c_str());
                rpl->start_position = rotate.position;
            }
        } else if (evt == TABLE_MAP_EVENT) {
            uint64_t table_id = new_event->event.table_map.table_id;
            if (m_table_cache.find(table_id) == m_table_cache.end()) {
                MARIADB_RPL_EVENT* tmp = new MARIADB_RPL_EVENT;
                *tmp = *new_event;
                auto& tm = new_event->event.table_map;
                if (tm.database.length > 0) {
                    tmp->event.table_map.database.str = new char[tm.database.length + 1];
                    memcpy(tmp->event.table_map.database.str, tm.database.str, tm.database.length);
                    tmp->event.table_map.database.str[tm.database.length] = '\0';
                }
                if (tm.table.length > 0) {
                    tmp->event.table_map.table.str = new char[tm.table.length + 1];
                    memcpy(tmp->event.table_map.table.str, tm.table.str, tm.table.length);
                    tmp->event.table_map.table.str[tm.table.length] = '\0';
                }
                if (tm.column_types.length > 0) {
                    tmp->event.table_map.column_types.str = new char[tm.column_types.length + 1];
                    memcpy(tmp->event.table_map.column_types.str, tm.column_types.str, tm.column_types.length);
                    tmp->event.table_map.column_types.str[tm.column_types.length] = '\0';
                }
                if (tm.metadata.length > 0) {
                    tmp->event.table_map.metadata.str = new char[tm.metadata.length + 1];
                    memcpy(tmp->event.table_map.metadata.str, tm.metadata.str, tm.metadata.length);
                    tmp->event.table_map.metadata.str[tm.metadata.length] = '\0';
                    tmp->event.table_map.metadata.length = tm.metadata.length;
                } else {
                    tmp->event.table_map.metadata.str = nullptr;
                    tmp->event.table_map.metadata.length = 0;
                }
                m_table_cache[table_id] = tmp;
            }
        } else if (evt == WRITE_ROWS_EVENT_V1 || evt == 30 ||
                   evt == UPDATE_ROWS_EVENT_V1 || evt == 31 ||
                   evt == DELETE_ROWS_EVENT_V1 || evt == 32) {
            uint64_t table_id = new_event->event.rows.table_id;
            auto it = m_table_cache.find(table_id);
            if (it == m_table_cache.end()) {
                cout << "Error: No TABLE_MAP for table_id " << table_id << '\n';
                cout.flush();
            } else {
                MARIADB_RPL_ROW *row = mariadb_rpl_extract_rows(rpl, it->second, new_event);
                if (!row) {
                    cout << "Failed to extract rows\n";
                    cout.flush();
                } else {
                    while (row) {
                        MARIADB_RPL_VALUE &val = row->columns[0];
                        cout << "table " << m_table[table_id] << " : ";
                        if (val.is_null) {
                            cout << "NULL\n";
                        } else {
                            if (val.field_type == MYSQL_TYPE_LONG)
                                cout << (int32_t)val.val.ll << '\n';
                            else cout << "val is not int.\n";
                        }
                        cout.flush();
                        row = row->next;
                    }
                }
            }
        }
    }
    if (new_event) mariadb_free_rpl_event(new_event);
    mariadb_rpl_close(rpl);
}

std::pair<string,uint32_t> Mysql_inf::get_latest_binlog() {
    pair<string,uint32_t> tmp;
    if (mysql_query(m_conn, "SHOW MASTER STATUS") == 0) {
        MYSQL_RES* res = mysql_store_result(m_conn);
        if (res) {
            MYSQL_ROW row = mysql_fetch_row(res);
            if (row) {
                tmp.first = std::string(row[0]);
                tmp.second = strtoull(row[1], nullptr, 10);
                cout << "Master status: " << tmp.first << " pos=" << tmp.second << "\n";
            }
            mysql_free_result(res);
        } else {
            cout << "Get Latest BinLog error\n;";
            exit(1);
        }
    }  else {
        cout << "Query error: " << mysql_error(m_conn) << "\n";
        cout << "Error code: " << mysql_errno(m_conn) << "\n";
    }
    return tmp;
}

void Mysql_inf::conn_init() {
    if(m_conn) mysql_close(m_conn);
    m_conn = mysql_init(nullptr);
    if(m_conn == nullptr){
        std::cout<<" mysql init error!\n";
        exit(1);
    }
    int read_timeout = 60;
    mysql_options(m_conn, MYSQL_OPT_READ_TIMEOUT, &read_timeout);
    if(!mysql_real_connect(m_conn,ip.c_str(),user.c_str(),passwd.c_str(), nullptr,port, nullptr,0)){
        std::cout << "ERROR: " << mysql_error(m_conn) << "\n";
        std::cout << "Error code: " << mysql_errno(m_conn) << "\n";
        mysql_close(m_conn);
        exit(2);
    }
    else {
        cout << "connect success!\n";
        m_state.store(true,memory_order_relaxed);

        table_init();
    }
}

MARIADB_RPL *Mysql_inf::rpl_init(uint32_t server_id) {
    MARIADB_RPL* rpl = mariadb_rpl_init(m_conn);
    if(!rpl){
        cout << "Init rpl error!\n";
        exit(1);
    }
    rpl->server_id = server_id;
    pair<string,uint32_t> latest_binlog = get_latest_binlog();
    rpl->filename = strdup(latest_binlog.first.c_str());
    rpl->start_position = latest_binlog.second;
    rpl->flags = 0;
//    rpl->use_checksum = 1;
//    rpl->artificial_checksum = 0;
//    mariadb_rpl_optionsv(rpl, MARIADB_RPL_VERIFY_CHECKSUM, 1);

    if (mysql_query(m_conn, "SET @master_binlog_checksum = @@global.binlog_checksum")) {
        cout << "Set checksum failed: " << mysql_error(m_conn) << '\n';
        exit(1);
    }
    mysql_query(m_conn, "SHOW VARIABLES LIKE 'binlog_checksum'");
    show_all_result();
    if (mariadb_rpl_open(rpl)) {
        cout <<"RPL open failed: " << rpl->error_msg << '\n';
        exit(1);
    }
    return rpl;
}

void Mysql_inf::table_init() {
    mysql_query(m_conn,"select TABLE_ID, NAME from information_schema.INNODB_SYS_TABLES;");
    m_res = mysql_store_result(m_conn);
    unsigned int field = 0;
    if(m_res)
        field = mysql_num_fields(m_res);
    else return;
    MYSQL_ROW row;
    while((row = mysql_fetch_row(m_res))){
        m_table.insert(make_pair(stoll(row[0]), strdup(row[1])));
    }
    mysql_free_result(m_res);
    m_res = nullptr;
}

std::vector<std::vector<std::string>> Mysql_inf::get_all_result() {
    vector<vector<string>> res;
    m_res = mysql_store_result(m_conn);
    unsigned int field = 0;
    if(m_res)
        field = mysql_num_fields(m_res);
    else return {};
    MYSQL_ROW row;
    while((row = mysql_fetch_row(m_res))){
        vector<string> val;
        for(int i = 0; i < field; i++)
            val.emplace_back(row[i] ? row[i] : "NULL");
        res.emplace_back(std::move(val));
    }
    mysql_free_result(m_res);
    m_res = nullptr;
    return std::move(res);
}


