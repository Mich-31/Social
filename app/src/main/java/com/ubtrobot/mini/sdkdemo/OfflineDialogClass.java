package com.ubtrobot.mini.sdkdemo;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OfflineDialogClass {
    private static Map<String, String> questions = new HashMap<>();

    public static void loadQuestionAnswer(Context context) {
        // Parse the JSON file
        InputStream in = context.getResources().openRawResource(R.raw.questions);
        JsonParser parser = new JsonParser();
        JsonObject jsonObject = parser.parse(new InputStreamReader(in)).getAsJsonObject();
        JsonArray questionsArray = jsonObject.getAsJsonArray("questions");

        // Iterate through each question
        for (JsonElement element : questionsArray) {
            JsonObject questionObject = element.getAsJsonObject();
            String question = questionObject.get("question").getAsString();
            String answer = questionObject.get("answer").getAsString();

            // Print the question and answer
            System.out.println("Question: " + question);
            System.out.println("Answer: " + answer);
            System.out.println();

            questions.put(question, answer);
        }
    }

    public static String getResponse(String question) {

        String[] question_split = question.split(" ");
        String answer = "";
        float diff_max = -100;

        for (Map.Entry<String, String> question_map: questions.entrySet()) {
            float diff = 0;
            int i = 0;

            for(String q : question_split) {
                if(question_map.getKey().contains(q)) {
                    i = i + 1;
                }
            }

            diff = i - question_split.length/2;

            if(diff > diff_max) {
                answer = question_map.getValue();
                diff_max = diff;
            }
        }

        return answer;
    }
}
