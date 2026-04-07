package DataBaseInf;

import com.sun.jna.Library;
import com.sun.jna.Pointer;

public interface MysqlInfLib extends Library {
    // 构造 / 析构
    Pointer Mysql_inf_create();
    Pointer Mysql_inf_create_with_params(String ip, int port, String user, String passwd);
    void    Mysql_inf_destroy(Pointer obj);

    // 方法
    boolean Mysql_inf_is_connected(Pointer obj);
    boolean Mysql_inf_select_database(Pointer obj, String db);
    void    Mysql_inf_show_all_result(Pointer obj);
    void    Mysql_inf_show_all_result_byone(Pointer obj);
    void    Mysql_inf_query(Pointer obj, String str);
    void    Mysql_inf_get_binlog(Pointer obj);
}