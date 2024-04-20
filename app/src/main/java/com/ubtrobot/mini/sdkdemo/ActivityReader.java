package com.ubtrobot.mini.sdkdemo;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class ActivityReader {

    private static Map<String, String> actions = new HashMap<>();

    public static void main(String[] args) {
        //Map<String, String> activities = readActivitiesFromFile("activities.json");

        // Print the activities
//        for (Map.Entry<String, String> entry : activities.entrySet()) {
//            System.out.println("Key: " + entry.getKey() + ", Value: " + entry.getValue());
//        }
    }

    public static void readActivitiesFromFile(Context context) {
        // Parse the JSON file
        InputStream in = context.getResources().openRawResource(R.raw.skill);
        JsonParser parser = new JsonParser();
        JsonObject jsonObject = parser.parse(new InputStreamReader(in)).getAsJsonObject();
        JsonArray questionsArray = jsonObject.getAsJsonArray("actions");

        // Iterate through each question
        for (JsonElement element : questionsArray) {
            JsonObject questionObject = element.getAsJsonObject();
            String key = questionObject.get("key").getAsString();
            String action = questionObject.get("action").getAsString();

            // Print the question and answer
            System.out.println("key: " + key);
            System.out.println("action: " + action);
            System.out.println();

            actions.put(key, action);
        }
    }

    public static String getAction(String key) {

        String[] key_split = key.split(" ");
        String action = "";

        for (Map.Entry<String, String> actions_map: actions.entrySet()) {
            float diff;
            int i = 0;

            String map_key = "ehi mario " + actions_map.getKey();

            for(String q : key_split) {
                if(map_key.contains(q)) {
                    i = i + 1;
                }
            }

            diff = (float) (i - (0.75 * key_split.length));

            if(diff > 0)
                action = actions_map.getValue();
        }

        return action;
    }
}
