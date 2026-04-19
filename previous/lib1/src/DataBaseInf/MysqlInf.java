package DataBaseInf;

import com.sun.jna.Native;
import com.sun.jna.Pointer;

public class MysqlInf implements AutoCloseable {
    private final Pointer handle;
    private static final MysqlInfLib lib;
    static {
        System.setProperty("jna.library.path", System.getProperty("user.dir") + "/lib");
        lib = Native.load("Mysql_inf", MysqlInfLib.class);
    }

    /** 默认构造：ip=127.0.0.1, port=3306, user=root */
    public MysqlInf() {
        handle = lib.Mysql_inf_create();
    }

    /** 有参构造 */
    public MysqlInf(String ip, int port, String user, String passwd) {
        handle = lib.Mysql_inf_create_with_params(ip, port, user, passwd);
    }

    public boolean isConnected() {
        return lib.Mysql_inf_is_connected(handle);
    }

    public boolean selectDatabase(String db) {
        return lib.Mysql_inf_select_database(handle, db);
    }

    public void query(String sql) {
        lib.Mysql_inf_query(handle, sql);
    }

    public void showAllResult() {
        lib.Mysql_inf_show_all_result(handle);
    }

    public void showAllResultByOne() {
        lib.Mysql_inf_show_all_result_byone(handle);
    }

    public void getBinlog() {
        lib.Mysql_inf_get_binlog(handle);
    }

    /** 支持 try-with-resources 自动释放 */
    @Override
    public void close() {
        lib.Mysql_inf_destroy(handle);
    }

    public static void main(String[] args) {
        // try-with-resources 自动调用析构函数，不会内存泄漏
        try (MysqlInf db = new MysqlInf("", 3306, "root", "123456")) {

            System.out.println("已连接: " + db.isConnected());

            db.getBinlog();
        }
    }
}
