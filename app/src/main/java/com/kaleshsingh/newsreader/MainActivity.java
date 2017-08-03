package com.kaleshsingh.newsreader;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    ListView listView;
    ArrayList<String> titles;
    ArrayList<String> content;
    ArrayAdapter<String> arrayAdapter;
    SQLiteDatabase articlesDB;

    public void updateListView(){
        Cursor c = articlesDB.rawQuery("SELECT * FROM articles", null);

        int titleIndex = c.getColumnIndex("title");
        int contentIndex = c.getColumnIndex("content");

        if(c.moveToFirst()){
            titles.clear();
            content.clear();

            do{
                titles.add(c.getString(titleIndex));
                content.add(c.getString(contentIndex));
            }while(c.moveToNext());
        }

        c.close();

        arrayAdapter.notifyDataSetChanged();
    }

    private class DownloadTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {

            String result = "";
            URL url;
            HttpURLConnection urlConnection;

            try {
                url = new URL(params[0]);
                urlConnection = (HttpURLConnection) url.openConnection();
                InputStream inputStream = urlConnection.getInputStream();
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                int data = inputStreamReader.read();
                while (data != -1) {
                    char currentCharacter = (char) data;
                    result += currentCharacter;
                    data = inputStreamReader.read();
                }

//                Log.i("URL Content", result);

                JSONArray jsonArray = new JSONArray(result);

                int numberOfItems = 20;

                if (jsonArray.length() < numberOfItems) {
                    numberOfItems = jsonArray.length();
                }

                articlesDB.execSQL("DELETE FROM articles");

                for (int i = 0; i < numberOfItems; ++i) {
//                    Log.i("JSONItem", jsonArray.getString(i));

                    String articleID = jsonArray.getString(i);

                    String articleInfo = "";
                    url = new URL("https://hacker-news.firebaseio.com/v0/item/" + articleID + ".json?print=pretty");
                    urlConnection = (HttpURLConnection) url.openConnection();
                    inputStream = urlConnection.getInputStream();
                    inputStreamReader = new InputStreamReader(inputStream);
                    data = inputStreamReader.read();
                    while (data != -1) {
                        char currentCharacter = (char) data;
                        articleInfo += currentCharacter;
                        data = inputStreamReader.read();
                    }

                    Log.i("ArticleInfo", articleInfo);

                    JSONObject jsonObject = new JSONObject(articleInfo);

                    if (!jsonObject.isNull("title") && !jsonObject.isNull("url")) {
                        String articleTitle = jsonObject.getString("title");
                        String articleURL = jsonObject.getString("url");

                        Log.i("info", articleTitle + " " + articleURL);

                        String articleContent = "";
                        url = new URL(articleURL);
                        urlConnection = (HttpURLConnection) url.openConnection();
                        inputStream = urlConnection.getInputStream();
                        inputStreamReader = new InputStreamReader(inputStream);
                        data = inputStreamReader.read();
                        while (data != -1) {
                            char currentCharacter = (char) data;
                            articleContent += currentCharacter;
                            data = inputStreamReader.read();
                        }

                        Log.i("Article Content", articleContent);

                        String sql = "INSERT INTO articles (articleID, title, content) VALUES (?, ?, ?)";
                        SQLiteStatement statement = articlesDB.compileStatement(sql);
                        statement.bindString(1, articleID);
                        statement.bindString(2, articleTitle);
                        statement.bindString(3, articleContent);
                        statement.execute();

                    }
                }

            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }

            return null;
        }


        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);

            updateListView();
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = (ListView) findViewById(R.id.listView);
        titles = new ArrayList<>();
        content = new ArrayList<>();
        arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, titles);
        listView.setAdapter(arrayAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(getApplicationContext(), ArticleActivity.class);
                intent.putExtra("content", content.get(position));

                startActivity(intent);
            }
        });

        articlesDB = this.openOrCreateDatabase("Articles", MODE_PRIVATE, null);
        articlesDB.execSQL("CREATE TABLE IF NOT EXISTS articles (_id INTEGER PRIMARY KEY, articleID INTEGER, title VARCHAR, content VARCHAR)");

        updateListView();

        DownloadTask task = new DownloadTask();
        try {
            task.execute("https://hacker-news.firebaseio.com/v0/topstories.json?print=pretty");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
