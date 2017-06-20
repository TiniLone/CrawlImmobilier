package crawlers;

import liquibase.util.StreamUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.*;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.GzipDecompressingEntity;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.client.protocol.ClientContext;

import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.apache.http.protocol.HttpContext;
import org.joda.time.LocalDateTime;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Baitshely {

    private static DefaultHttpClient httpClient = createClient();
    private static String entryPoint = "http://www.baitshely.com/property?city=Isra%C3%ABl&datefrom=01%2F02%2F2017&dateto=01%2F31%2F2017&guests=";
    private static String host;
    private static String siteDomain;

    public static void main(String[] args) throws IOException {
        configureHostDomain();
        boolean continueCrawl = true;
        BufferedWriter fileToSave = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("D:\\CP\\ContentWeb\\" + host + "-" + LocalDateTime.now().toString().replace(":", "_") + ".csv"), "cp1252"));
        fileToSave.append("TITLE;LINK OFFER;IMAGES;OWNER;TEL;LOCALITY;\n");
        int loop = 0;
        while (continueCrawl) {
            loop++;
            Page listingPage;
            if (loop == 1) listingPage = getConnection(entryPoint, "listing");
            else {
                Map<String, String> nameValuePairs = getParameters(loop - 1);
                listingPage = postConnection(entryPoint, nameValuePairs);
            }
            System.out.println();
            System.out.println("Page : " + loop);
            Elements scriptElements = Jsoup.parse(listingPage.getContent()).select("script");
            String scriptContent = getScript(scriptElements, "function downloadUrl()");
            String [] scriptTab = new String[0];
            if (scriptContent != null) {
                scriptTab = scriptContent.split("var point");
            }
            List<String> offerLinkTab = new ArrayList<String>();
            for (String script : scriptTab) {
                if (StringUtils.contains(script, "var html")) {
                    String content = StringUtils.substringBetween(script, "var html = '", "';");
                    Element contentElement = Jsoup.parse(content);
                    offerLinkTab.add(cleanPath("/" + contentElement.select("span.headlined > a").attr("href")));
                }
            }

            for (int offerIndex = 0; offerIndex < offerLinkTab.size(); offerIndex++) {
                System.out.println();
                System.out.println("Offer : " + offerIndex);

                String linkOffer = offerLinkTab.get(offerIndex);
                System.out.println("Link : " + linkOffer);

                fileToSave.append(crawlOfferPage(linkOffer));
            }

            if (offerLinkTab.size() == 0) continueCrawl = false;

            entryPoint = "http://www.baitshely.com/site/rentals/mapViewAjax";
        }
        fileToSave.close();
        httpClient.getConnectionManager().shutdown();
    }

    private static Map<String,String> getParameters(int i) {
        Map<String, String> map = new HashMap<String, String>();
        map.put("paginationId", "" + (20 * i));
        map.put("pricemin", "");
        map.put("pricemax", "");
        map.put("checkin", "01/02/2017");
        map.put("checkout", "01/31/2017");
        map.put("guests", "");
        map.put("minLat", "27.93901912897453");
        map.put("minLong", "29.324979612499988");
        map.put("maxLat", "34.79839999030318");
        map.put("maxLong", "40.83865148749999");
        map.put("cLat", "31.368709559638855");
        map.put("cLong", "35.08181554999999");
        map.put("zoom", "6");
        return map;
    }

    private static String crawlOfferPage(String linkOffer) {
        Page offerPage = getConnection(linkOffer, "offer");
        Document offerDocument = Jsoup.parse(offerPage.getContent());

        String title = offerDocument.select("h2.titled").first().text();
        title = title.replace(";", ",");
        System.out.println("Title : " + title);

        String images = getImage(offerDocument);
        System.out.println("Images : " + images);

        String owner = offerDocument.select("span.pep-name").text();
        System.out.println("Owner : " + owner);

        String tel = offerDocument.select("div.tel").text();
        System.out.println("Tel : " + tel);

        String locality = offerDocument.select("a.link-plce").text();
        System.out.println("Locality : " + locality);

        return title + ";" + linkOffer + ";" + images + ";" + owner + ";" + tel + ";" + locality + ";\n";
    }

    private static String getImage(Document offer) {
        String images = "";
        Elements imagesElements = offer.select("article.descri-section > div.slide li > a > img");
        for (Element imagesElement : imagesElements) {
            images += cleanPath(imagesElement.attr("src")) + ",";
        }
        return images;
    }

     private static String getScript(Elements scriptsElements, String content) {
         for (Element scriptElement : scriptsElements) {
             String script = scriptElement.data();
             if (!StringUtils.isEmpty(script)) {
                 if (script.contains(content)) {
                     return script.trim();
                 }
             }
         }
         return null;
     }

    private static void configureHostDomain() {
        URL url = null;
        try {
            url = new URL(entryPoint);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        if (url != null) {
            host = url.getHost();
            final String protocol = url.getProtocol();
            siteDomain = protocol + "://" + host;
        }
    }

    private static DefaultHttpClient createClient() {
        DefaultHttpClient httpclient = new DefaultHttpClient();
        httpclient.getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 30000);
        httpclient.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, 60000);
        httpclient.getParams().setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.BROWSER_COMPATIBILITY);
        return httpclient;
    }

    private static Page postConnection(final String url, final Map<String, String> nameValuePairs) {
        int status = 0;
        int tryConn = 0;
        System.out.println("Post Connect on url : " + url);

        String pageUrl = url;
        Page page = new Page();
        while (status != HttpStatus.SC_OK && tryConn < 5) {
            ++tryConn;
            try {
                HttpContext localContext = context();
                HttpPost httppost = postMethod(allowHttpRedirection(false), pageUrl);

                ArrayList<NameValuePair> parameters = new ArrayList<NameValuePair>();
                for (Map.Entry<String, String> entry : nameValuePairs.entrySet()) {
                    parameters.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
                }
                httppost.setEntity(new UrlEncodedFormEntity(parameters, HTTP.UTF_8));

                HttpResponse response = httpClient.execute(httppost, localContext);
                HttpEntity entity = response.getEntity();

                status = response.getStatusLine().getStatusCode();
                System.out.println("Post status : " + status);

                if (status == HttpStatus.SC_OK) {

                        final String content = getContent(response, entity);
                        page.setContent(content);

                } else if (status == HttpStatus.SC_MOVED_PERMANENTLY || status == HttpStatus.SC_MOVED_TEMPORARILY) {
                    String location = getLocation(response);
                    System.out.println("Location : " + location);
                    pageUrl = location;
                }

                if (entity != null) {
                    EntityUtils.consume(entity);
                }
            } catch (Exception exc) {
                System.out.println("Connection parse error [" + url + "] - Exc : " + exc);
            }
        }

        return page;
    }

    private static HttpPost postMethod(HttpParams params, String url) {
        HttpPost httpPost = new HttpPost(url);
        httpPost.addHeader("Accept", "*/*");
        httpPost.addHeader("Accept-Language", "fr,fr-FR;q=0.8,en-US;q=0.5,en;q=0.3");
        httpPost.addHeader("Cache-Control", "no-cache");
        httpPost.addHeader("Connection", "keep-alive");
        httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        httpPost.addHeader("Host", host);
        httpPost.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:42.0) Gecko/20100101 Firefox/42.0");
        httpPost.addHeader("X-Requested-With", "XMLHttpRequest");

        if (params != null) {
            httpPost.setParams(params);
        }
        return httpPost;
    }

    private static Page getConnection(final String url, final String type) {
        int status = 0;
        int tryConn = 0;
        System.out.println("Connect on " + type + " url : " + url);

        String pageUrl = url;
        Page page = new Page();
        while (status != HttpStatus.SC_OK && tryConn < 5) {
            ++tryConn;
            try {
                HttpContext localContext = context();
                HttpGet httpget = getMethod(allowHttpRedirection(false), pageUrl);
                HttpResponse response = httpClient.execute(httpget, localContext);
                HttpEntity entity = response.getEntity();

                status = response.getStatusLine().getStatusCode();
                System.out.println("Connection status : " + status);

                if (status == HttpStatus.SC_OK) {
                    final String content = getContent(response, entity);
                    page.setContent(content);
                } else if (status == HttpStatus.SC_MOVED_PERMANENTLY || status == HttpStatus.SC_MOVED_TEMPORARILY) {
                    String location = getLocation(response);
                    System.out.println("Location : " + location);
                    pageUrl = location;
                }

                if (entity != null) {
                    EntityUtils.consume(entity);
                }
            } catch (Exception exc) {
                System.out.println("Connection parse error [" + url + "] - Exc : " + exc);
            }
        }
        return page;
    }

    private static String getLocation(HttpResponse response) {
        Header locationHeader = response.getFirstHeader("Location");
        if (locationHeader != null) {
            String location = locationHeader.getValue();
            return cleanPath(location);
        }
        return null;
    }

    private static String cleanPath(String path) {
        if (path.startsWith("//")) return "https:" + path;
        if (!path.startsWith("http")) return siteDomain + path;
        return path;
    }

    private static String getContent(HttpResponse response, HttpEntity entity) throws IOException {
        Header contentEncodingHeader = response.getFirstHeader("Content-Encoding");
        if (contentEncodingHeader != null) {
            String encoding = contentEncodingHeader.getValue();
            if (encoding.contains("gzip")) {
                InputStreamReader reader = new InputStreamReader(new GzipDecompressingEntity(entity).getContent(), "utf-8");
                String content = StreamUtil.getReaderContents(reader);
                return StringUtils.trim(content);
            }
        }
        return StringUtils.trim(EntityUtils.toString(entity));
    }

    private static HttpGet getMethod(HttpParams params, String url) {
        HttpGet httpGet = new HttpGet(url);
        httpGet.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        httpGet.addHeader("Accept-Encoding", "gzip, deflate");
        httpGet.addHeader("Accept-Language", "fr-FR,fr;q=0.8,en-US;q=0.6,en;q=0.4");
        httpGet.addHeader("Connection", "keep-alive");
        httpGet.addHeader("Host", host);
//        httpGet.addHeader("Referer", "");
        httpGet.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/45.0.2454.101 Safari/537.36");

        if (params != null) {
            httpGet.setParams(params);
        }
        return httpGet;
    }

    private static HttpContext context() {
        CookieStore cookieStore = httpClient.getCookieStore();
        HttpContext localContext = new BasicHttpContext();
        localContext.setAttribute(ClientContext.COOKIE_STORE, cookieStore);
        return localContext;
    }

    private static HttpParams allowHttpRedirection(boolean redirection) {
        HttpParams params = new BasicHttpParams();
        params.setParameter(ClientPNames.HANDLE_REDIRECTS, redirection);
        params.setParameter(ClientPNames.ALLOW_CIRCULAR_REDIRECTS, false);
        return params;
    }

    private static class Page {
        private String content;

        private String getContent() {
            return content;
        }

        private void setContent(String content) {
            this.content = content;
        }
    }
}
