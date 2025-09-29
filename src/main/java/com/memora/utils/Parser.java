package com.memora.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Parser {

    private static final Gson gson = new GsonBuilder().create();

    public static <T> String toJson(T object) {
        return gson.toJson(object);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return gson.fromJson(json, clazz);
    }
}
