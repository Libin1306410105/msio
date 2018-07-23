package com.hellozq.msio.bean.common;

import com.esotericsoftware.reflectasm.MethodAccess;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.text.SimpleDateFormat;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author bin
 * 静态中间变量，
 * 能够作为不改变的工具数据一直存在的变量
 */
@SuppressWarnings("unused")
public class CommonBean {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

}
