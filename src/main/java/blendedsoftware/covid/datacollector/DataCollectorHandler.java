package blendedsoftware.covid.datacollector;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;


public class DataCollectorHandler implements RequestHandler<Object, Object> {

    private static final ObjectMapper jsonMapper = new ObjectMapper();
    private static String bucketName;
    private static boolean s3Enabled = false;

    static {
        String s3EnabledEnvVar = System.getenv("S3_ENABLED");
        if (s3EnabledEnvVar != null && !s3EnabledEnvVar.isBlank()) {
            bucketName = System.getenv("BUCKET_NAME");
            s3Enabled = Boolean.valueOf(s3EnabledEnvVar);
        }
    }

    public Object handleRequest(final Object input, final Context context) {
        CovidData covidData = new CovidData();
        try {
            var doc = Jsoup.connect("https://www.worldometers.info/coronavirus/#countries").get();

            Iterator<Element> overviewNumberElements = doc.select("#maincounter-wrap > div > span").iterator();

            covidData.activeCases = overviewNumberElements.next().text();
            covidData.totalDeaths = overviewNumberElements.next().text();
            covidData.totalRecovered = overviewNumberElements.next().text();

            covidData.countries.putAll(doc
                .select("#main_table_countries > tbody:nth-child(2) > tr")
                .stream()
                .map(cr -> {
                    Iterator<Element> rowCells = cr.children().iterator();

                    CovidData.Country country = new CovidData.Country();
                    country.name = rowCells.next().text();
                    country.totalCases = rowCells.next().text();
                    country.newCases = rowCells.next().text();
                    country.totalDeaths = rowCells.next().text();
                    country.newDeaths = rowCells.next().text();
                    country.totalRecovered = rowCells.next().text();
                    country.activeCases = rowCells.next().text();
                    return country;
                })
                .collect(Collectors.toMap(c -> c.name.toLowerCase(), c -> c))
            );

            if (s3Enabled) {
                String key = "covid_data.json"; // TODO: create a name based convention on runtime

                // TODO: Calculate difference from yesterday - compute growth rate

                S3Client s3 = S3Client.builder().build();
                s3.putObject(
                        PutObjectRequest.builder()
                                .bucket(bucketName)
                                .key(key)
                                .build(),
                        RequestBody.fromBytes(jsonMapper.writeValueAsBytes(covidData))
                );
            }

            return jsonMapper.writeValueAsString(covidData);
        }
        catch (IOException e) {
            e.printStackTrace();
            return new JSONObject().put("result", "failed").toString();
        }
    }

    public static void main(String[] args) {
        DataCollectorHandler function = new DataCollectorHandler();
        System.out.println(function.handleRequest(null, null));
    }

    public static class CovidData {
        public String activeCases;
        public String totalDeaths;
        public String totalRecovered;
        public Map<String, Country> countries = new TreeMap<>();

        public static class Country {
            public String name;
            public String totalCases;
            public String newCases;
            public String totalDeaths;
            public String newDeaths;
            public String totalRecovered;
            public String activeCases;
        }
    }
}
