package org.schabi.newpipe.extractor.services.soundcloud;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.Downloader;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.stream.StreamInfoItemCollector;
import org.schabi.newpipe.extractor.utils.Parser;
import org.schabi.newpipe.extractor.utils.Parser.RegexException;

import java.io.IOException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SoundcloudParsingHelper {
    private static String clientId;

    private SoundcloudParsingHelper() {
    }

    public static String clientId() throws ReCaptchaException, IOException, RegexException {
        if (clientId != null && !clientId.isEmpty()) return clientId;

        Downloader dl = NewPipe.getDownloader();

        String response = dl.download("https://soundcloud.com");
        Document doc = Jsoup.parse(response);

        // TODO: Find a less heavy way to get the client_id
        // Currently we are downloading a 1MB file (!) just to get the client_id,
        // youtube-dl don't have a way too, they are just hardcoding and updating it when it becomes invalid.
        // The embed mode has a way to get it, but we still have to download a heavy file (~800KB).
        Element jsElement = doc.select("script[src^=https://a-v2.sndcdn.com/assets/app]").first();
        String js = dl.download(jsElement.attr("src"));

        clientId = Parser.matchGroup1(",client_id:\"(.*?)\"", js);
        return clientId;
    }

    public static String toDateString(String time) throws ParsingException {
        try {
            Date date;
            // Have two date formats, one for the 'api.soundc...' and the other 'api-v2.soundc...'.
            try {
                date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(time);
            } catch (Exception e) {
                date = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss +0000").parse(time);
            }

            SimpleDateFormat newDateFormat = new SimpleDateFormat("yyyy-MM-dd");
            return newDateFormat.format(date);
        } catch (ParseException e) {
            throw new ParsingException(e.getMessage(), e);
        }
    }

    /**
     * Call the endpoint "/resolve" of the api.<br/>
     * See https://developers.soundcloud.com/docs/api/reference#resolve
     */
    public static JSONObject resolveFor(String url) throws IOException, ReCaptchaException, ParsingException {
        String apiUrl = "https://api.soundcloud.com/resolve"
                + "?url=" + URLEncoder.encode(url, "UTF-8")
                + "&client_id=" + clientId();

        return new JSONObject(NewPipe.getDownloader().download(apiUrl));
    }

    /**
     * Fetch the embed player with the apiUrl and return the canonical url (like the permalink_url from the json api).<br/>
     *
     * @return the url resolved
     */
    public static String resolveUrlWithEmbedPlayer(String apiUrl) throws IOException, ReCaptchaException, ParsingException {

        String response = NewPipe.getDownloader().download("https://w.soundcloud.com/player/?url="
                + URLEncoder.encode(apiUrl, "UTF-8"));

        return Jsoup.parse(response).select("link[rel=\"canonical\"]").first().attr("abs:href");
    }

    /**
     * Fetch the embed player with the url and return the id (like the id from the json api).<br/>
     *
     * @return the id resolved
     */
    public static String resolveIdWithEmbedPlayer(String url) throws IOException, ReCaptchaException, ParsingException {

        String response = NewPipe.getDownloader().download("https://w.soundcloud.com/player/?url="
                + URLEncoder.encode(url, "UTF-8"));
        return Parser.matchGroup1(",\"id\":(.*?),", response);
    }

    /**
     * Fetch the streams from the given api and commit each of them to the collector.
     * <p>
     * This differ from {@link #getStreamsFromApi(StreamInfoItemCollector, String)} in the sense that they will always
     * get MIN_ITEMS or more items.
     *
     * @param minItems the method will return only when it have extracted that many items (equal or more)
     */
    public static String getStreamsFromApiMinItems(int minItems, StreamInfoItemCollector collector, String apiUrl) throws IOException, ReCaptchaException, ParsingException {
        String nextStreamsUrl = SoundcloudParsingHelper.getStreamsFromApi(collector, apiUrl);

        while (!nextStreamsUrl.isEmpty() && collector.getItemList().size() < minItems) {
            nextStreamsUrl = SoundcloudParsingHelper.getStreamsFromApi(collector, nextStreamsUrl);
        }

        return nextStreamsUrl;
    }

    /**
     * Fetch the streams from the given api and commit each of them to the collector.
     *
     * @return the next streams url, empty if don't have
     */
    public static String getStreamsFromApi(StreamInfoItemCollector collector, String apiUrl) throws IOException, ReCaptchaException, ParsingException {
        String response = NewPipe.getDownloader().download(apiUrl);
        JSONObject responseObject = new JSONObject(response);

        JSONArray responseCollection = responseObject.getJSONArray("collection");
        for (int i = 0; i < responseCollection.length(); i++) {
            collector.commit(new SoundcloudStreamInfoItemExtractor(responseCollection.getJSONObject(i)));
        }

        String nextStreamsUrl;
        try {
            nextStreamsUrl = responseObject.getString("next_href");
            if (!nextStreamsUrl.contains("client_id=")) nextStreamsUrl += "&client_id=" + SoundcloudParsingHelper.clientId();
        } catch (Exception ignored) {
            nextStreamsUrl = "";
        }

        return nextStreamsUrl;
    }
}
