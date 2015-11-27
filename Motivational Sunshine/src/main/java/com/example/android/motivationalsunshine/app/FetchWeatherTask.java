package com.example.android.motivationalsunshine.app;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ArrayAdapter;

import com.example.android.motivationalsunshine.app.data.WeatherContract;
import com.example.android.motivationalsunshine.app.data.WeatherContract.WeatherEntry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Vector;


public class FetchWeatherTask extends AsyncTask<String, Void, Void> {
    private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();
    private final Context mContext;
    private ArrayAdapter<String> mForecastAdapter;

    public FetchWeatherTask(Context context) {
        mContext = context;
//        mForecastAdapter = forecastAdapter;
    }

    /**
     * Helper method to handle insertion of a new location in the weather database.
     *
     * @param locationSetting The location string used to request updates from the server.
     * @param cityName        A human-readable city name, e.g "Mountain View"
     * @param lat             the latitude of the city
     * @param lon             the longitude of the city
     * @return the row ID of the added location.
     */
    public long addLocation(String locationSetting, String cityName) {
        long locationId;

        // First, check if the location with this city name exists in the db
        Cursor locationCursor = mContext.getContentResolver().query(
                WeatherContract.LocationEntry.CONTENT_URI,
                new String[]{WeatherContract.LocationEntry._ID},
                WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING + " = ?",
                new String[]{locationSetting},
                null);

        if (locationCursor.moveToFirst()) {
            int locationIdIndex = locationCursor.getColumnIndex(WeatherContract.LocationEntry._ID);
            locationId = locationCursor.getLong(locationIdIndex);
        } else {
            // Now that the content provider is set up, inserting rows of data is pretty simple.
            // First create a ContentValues object to hold the data you want to insert.
            ContentValues locationValues = new ContentValues();

            // Then add the data, along with the corresponding name of the data type,
            // so the content provider knows what kind of value is being inserted.
            locationValues.put(WeatherContract.LocationEntry.COLUMN_CITY_NAME, cityName);
            locationValues.put(WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING, locationSetting);

            // Finally, insert location data into the database.
            Uri insertedUri = mContext.getContentResolver().insert(
                    WeatherContract.LocationEntry.CONTENT_URI,
                    locationValues
            );

            // The resulting URI contains the ID for the row.  Extract the locationId from the Uri.
            locationId = ContentUris.parseId(insertedUri);
        }

        locationCursor.close();
        // Wait, that worked?  Yes!
        return locationId;
    }

    protected Void doInBackground(String... params) {
        // Weather forecast snippet!
        // These two need to be declared outside the try/catch
        // so that they can be closed in the finally block.
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;
        int numDays = 4;
        String locationQuery = params[0];
        String country = "Italy";
        String language = "lang:";
        String apiKey = "abb806c94f5f568e";

        // Will contain theS raw JSON response as a string.
        String forecastJsonStr = null;

        // If there's no zip code, there's nothing to look up.  Verify size of params.
        if (params.length == 0) {
            return null;
        }

        String locale = mContext.getResources().getConfiguration().locale.getLanguage();
        language = language + locale.toUpperCase();

        try {
            // Construct the URL for the OpenWeatherMap query
            // Possible parameters are available at OWM's forecast API page, at
            // http://openweathermap.org/API#forecast
            final String FORECAST_BASE_URL = "http://api.wunderground.com/api/";

            // TODO fix request of location. Remove country and language and add check of returned JSON
            // TODO if location is ambiguous select from the returned list
            URL url = new URL(FORECAST_BASE_URL + apiKey + "/forecast/" + language + "/q/" + country + "//" + locationQuery + ".json");

            // Create the request to OpenWeatherMap, and open the connection
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.connect();

            // Read the input stream into a String
            InputStream inputStream = urlConnection.getInputStream();
            StringBuilder buffer = new StringBuilder();
            if (inputStream == null) {
                // Nothing to do.
                forecastJsonStr = null;
                return null;
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = reader.readLine()) != null) {
                // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                // But it does make debugging a *lot* easier if you print out the completed
                // buffer for debugging.
                buffer.append(line + "\n");
            }

            if (buffer.length() == 0) {
                // Stream was empty.  No point in parsing.
                return null;
            }
            forecastJsonStr = buffer.toString();

        } catch (IOException e) {
            Log.e(LOG_TAG, "Error ", e);
            // If the code didn't successfully get the weather data, there's no point in attemping
            // to parse it.
            return null;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (final IOException e) {
                    Log.e(LOG_TAG, "Error closing stream", e);
                }
            }
        }

        try {
            return getWeatherDataFromJson(forecastJsonStr, locationQuery, numDays);
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
        }

        // This will only happen if there was an error getting or parsing the forecast.
        return null;
    }


    /**
     * Take the String representing the complete forecast in JSON Format and
     * pull out the data we need to construct the Strings needed for the wireframes.
     * <p/>
     * Fortunately parsing is easy:  constructor takes the JSON string and converts it
     * into an Object hierarchy for us.
     */
    private Void getWeatherDataFromJson(String forecastJsonStr, String locationSetting, int numDays)
            throws JSONException {

        // These are the names of the JSON objects that need to be extracted.
        final String WUND_FORECAST = "forecast";
        final String WUND_SIMPLEFORECAST = "simpleforecast";
        final String WUND_FORECASTDAY = "forecastday";
        final String WUND_DATE = "date";
        final String WUND_EPOCH = "epoch";
        final String WUND_HIGH = "high";
        final String WUND_LOW = "low";
        final String WUND_TEMP_UNITS = "celsius";
        final String WUND_DESCRIPTION = "conditions";
        final String WUND_ICON = "icon";
        final String WUND_AVE_WIND = "avewind";
        final String WUND_WIND_DIR = "degrees";
        final String WUND_WIND_SPEED = "kph";
        final String WUND_HUMIDITY = "avehumidity";

        try {

            JSONObject jsonData = new JSONObject(forecastJsonStr);
            JSONObject forecastJsonData = jsonData.getJSONObject(WUND_FORECAST);
            JSONObject simpleForecastJsonData = forecastJsonData.getJSONObject(WUND_SIMPLEFORECAST);
            JSONArray weatherArray = simpleForecastJsonData.getJSONArray(WUND_FORECASTDAY);

            long locationId = addLocation(locationSetting, locationSetting);

            // Insert the new weather information into the database
            Vector<ContentValues> cVVector = new Vector<ContentValues>(weatherArray.length());

            for (int i = 0; i < weatherArray.length(); i++) {
                // These are the values that will be collected.
                String dayTime;
                int high;
                int low;
                int humidity;
                double windSpeed;
                double windDirection;
                String description;
                String iconName;

                // Get the JSON object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                JSONObject epoch = dayForecast.getJSONObject(WUND_DATE);
                dayTime = epoch.getString(WUND_EPOCH);

                //convert epoch time to java milliseconds
                dayTime = dayTime + "000";

                JSONObject highTemperature = dayForecast.getJSONObject(WUND_HIGH);
                high = highTemperature.getInt(WUND_TEMP_UNITS);

                JSONObject lowTemperature = dayForecast.getJSONObject(WUND_LOW);
                low = lowTemperature.getInt(WUND_TEMP_UNITS);
                iconName = dayForecast.getString(WUND_ICON);

                description = dayForecast.getString(WUND_DESCRIPTION);

                JSONObject wind = dayForecast.getJSONObject(WUND_AVE_WIND);
                windSpeed = wind.getDouble(WUND_WIND_SPEED);
                windDirection = wind.getDouble(WUND_WIND_DIR);

                humidity = dayForecast.getInt(WUND_HUMIDITY);

                ContentValues weatherValues = new ContentValues();

                weatherValues.put(WeatherEntry.COLUMN_LOC_KEY, locationId);
                weatherValues.put(WeatherEntry.COLUMN_DATE, dayTime);
                weatherValues.put(WeatherEntry.COLUMN_HUMIDITY, humidity);
//                weatherValues.put(WeatherEntry.COLUMN_PRESSURE, pressure);
                weatherValues.put(WeatherEntry.COLUMN_WIND_SPEED, windSpeed);
                weatherValues.put(WeatherEntry.COLUMN_DEGREES, windDirection);
                weatherValues.put(WeatherEntry.COLUMN_MAX_TEMP, high);
                weatherValues.put(WeatherEntry.COLUMN_MIN_TEMP, low);
                weatherValues.put(WeatherEntry.COLUMN_SHORT_DESC, description);
                weatherValues.put(WeatherEntry.COLUMN_WEATHER_ID, iconName);

                cVVector.add(weatherValues);
            }

            int inserted = 0;

            // add to database
            if (cVVector.size() > 0) {
                ContentValues[] cvArray = new ContentValues[cVVector.size()];
                cVVector.toArray(cvArray);

                inserted = mContext.getContentResolver().bulkInsert(WeatherEntry.CONTENT_URI, cvArray);
            }
            Log.d(LOG_TAG, "FetchWeatherTask Complete. " + inserted + " Inserted");

        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
        }

        return null;
    }
}
