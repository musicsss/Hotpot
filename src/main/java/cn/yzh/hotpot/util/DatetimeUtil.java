package cn.yzh.hotpot.util;

import org.bouncycastle.util.Times;

import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;

public class DatetimeUtil {
    public static Timestamp getNoonTimestamp(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 12);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return new Timestamp(calendar.getTimeInMillis());
    }

    public static Timestamp getTodayNoonTimestamp() {
        return getNoonTimestamp(new Date());
    }

    public static Timestamp getNowTimestamp() {
        return new Timestamp(new Date().getTime());
    }

    public static Timestamp long2Timestamp(long time) {
        return new Timestamp(time);
    }

    public static Timestamp getNextNoonTimestamp(Timestamp curDay) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(curDay);
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        return new Timestamp(calendar.getTimeInMillis());
    }

    public static void main(String[] args) {
        Date date = new Date();
        System.out.println(date);
        System.out.println(date.getTime());
        System.out.println(getNoonTimestamp(date).getTime());
    }
}
