package com.example.android.motivationalsunshine.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.example.android.motivationalsunshine.app.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment {
    private ArrayAdapter<String> mForecastAdapter;

    public ForecastFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Add this line in order for this fragment to handle menu events.
        setHasOptionsMenu(true);
    }
    @Override
    public void onStart(){
        super.onStart();
        updateWeather();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            updateWeather();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mForecastAdapter = new ArrayAdapter<>(
                getActivity(),
                R.layout.list_item_forecast,
                R.id.list_item_forecast_textview,
                new ArrayList<String>()
        );

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        final ListView listView = (ListView) rootView.findViewById(R.id.listview_forecast);
        listView.setAdapter(mForecastAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                String forecast = mForecastAdapter.getItem(i);
                Intent explicitIntent = new Intent(getActivity(), DetailActivity.class).putExtra(Intent.EXTRA_TEXT, forecast);
                startActivity(explicitIntent);
            }
        });

        return rootView;
    }

    private void updateWeather (){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        String location = preferences.getString(getString(R.string.pref_location_key), getString(R.string.pref_location_default));
        new FetchWeatherTask().execute(location);
    }

    public class FetchWeatherTask extends AsyncTask<String, Void, String[]> {
        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

        protected String[] doInBackground(String... params) {
            // Weather forecast snippet!
            // These two need to be declared outside the try/catch
            // so that they can be closed in the finally block.
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;
            int numDays = 4;
            String location = params[0];
            String country = "Italy/";
            String language = "lang:IT";
            String apiKey = "abb806c94f5f568e";

            // Will contain the raw JSON response as a string.
            String forecastJsonStr = null;

            try {
                // Construct the URL for the OpenWeatherMap query
                // Possible parameters are available at OWM's forecast API page, at
                // http://openweathermap.org/API#forecast
                final String FORECAST_BASE_URL = "http://api.wunderground.com/api/";

                URL url = new URL(FORECAST_BASE_URL + apiKey + "/forecast/" + language + "/q/" + country + location + ".json");

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
                return getWeatherDataFromJson(forecastJsonStr, numDays);
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            }

            // This will only happen if there was an error getting or parsing the forecast.
            return null;
        }

        @Override
        protected void onPostExecute(String[] strings) {
            super.onPostExecute(strings);

            mForecastAdapter.clear();
            mForecastAdapter.addAll(strings);
        }

        /**
         * Take the String representing the complete forecast in JSON Format and
         * pull out the data we need to construct the Strings needed for the wireframes.
         *
         * Fortunately parsing is easy:  constructor takes the JSON string and converts it
         * into an Object hierarchy for us.
         */
        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
                throws JSONException {

            // These are the names of the JSON objects that need to be extracted.
            final String WUND_FORECAST = "forecast";
            final String WUND_SIMPLEFORECAST = "simpleforecast";
            final String WUND_FORECASTDAY= "forecastday";
            final String WUND_HIGH = "high";
            final String WUND_LOW = "low";
            final String WUND_TEMP_UNITS = "celsius";
            final String WUND_DESCRIPTION = "conditions";

            JSONObject jsonData = new JSONObject(forecastJsonStr);
            JSONObject forecastJsonData = jsonData.getJSONObject(WUND_FORECAST);
            JSONObject simpleForecastJsonData = forecastJsonData.getJSONObject(WUND_SIMPLEFORECAST);
            JSONArray weatherArray = simpleForecastJsonData.getJSONArray(WUND_FORECASTDAY);

            String[] resultStrs = new String[numDays];

            //create a Gregorian Calendar, which is in current date
            GregorianCalendar gc = new GregorianCalendar();

            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
            String unitsType = preferences.getString(getString(R.string.pref_temperature_key), getString(R.string.pref_temperature_celsius));

            for (int i = 0; i < weatherArray.length(); i++) {
                // For now, using the format "Day, description, hi/low"
                String day;
                String description;

                // Get the JSON object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                //add i dates to current date of calendar
                gc.add(GregorianCalendar.DATE, 1);
                //get that date, format it, and "save" it on variable day
                Date time = gc.getTime();
                SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd", Locale.ITALY);
                day = shortenedDateFormat.format(time);

                JSONObject highTemperature = dayForecast.getJSONObject(WUND_HIGH);
                int high = highTemperature.getInt(WUND_TEMP_UNITS);

                JSONObject lowTemperature = dayForecast.getJSONObject(WUND_LOW);
                int low = lowTemperature.getInt(WUND_TEMP_UNITS);

                description = dayForecast.getString(WUND_DESCRIPTION);

                if (unitsType.equals(getString(R.string.pref_temperature_celsius))) {
                    resultStrs[i] = day + " - " + description + " - " + low + "C/" + high + "C";
                } else {
                    resultStrs[i] = day + " - " + description + " - " + ((low * 1.8) + 32) + "F/" + ((high * 1.8) + 32) + "F";
                }
            }
            return resultStrs;

        }
    }
}
