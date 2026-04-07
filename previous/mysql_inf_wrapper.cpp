#include "Mysql_inf.h"

extern "C" {

// ========== 构造 / 析构 ==========

Mysql_inf* Mysql_inf_create() {
    return new Mysql_inf();
}

Mysql_inf* Mysql_inf_create_with_params(const char* ip, uint32_t port,
                                        const char* user, const char* passwd) {
    return new Mysql_inf(ip, port, user, passwd);
}

void Mysql_inf_destroy(Mysql_inf* obj) {
    delete obj;
}

// ========== 公开方法 ==========

bool Mysql_inf_is_connected(Mysql_inf* obj) {
    return obj->is_connected();
}

bool Mysql_inf_select_database(Mysql_inf* obj, const char* db) {
    return obj->select_database(db);
}

void Mysql_inf_show_all_result(Mysql_inf* obj) {
    obj->show_all_result();
}

void Mysql_inf_show_all_result_byone(Mysql_inf* obj) {
    obj->show_all_result_byone();
}

void Mysql_inf_query(Mysql_inf* obj, const char* str) {
    obj->query(str);
}

void Mysql_inf_get_binlog(Mysql_inf* obj) {
    obj->get_binlog();
}

} // extern "C"