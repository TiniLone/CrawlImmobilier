package crawlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import liquibase.util.StreamUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.*;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.GzipDecompressingEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.params.ConnRouteParams;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
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

public class Homelidays {

    private static DefaultHttpClient httpClient = createClient();
    private static String entryPoint = "https://www.homelidays.com/search/israel/region:774/arrival:2016-12-30/departure:2017-01-31";
    private static String host;
    private static String siteDomain;

    public static void main(String[] args) throws IOException {
        configureHostDomain();
        boolean continueCrawl = true;
//        BufferedWriter fileToSave = new BufferedWriter(new FileWriter("D:\\" + host + "-" + LocalDateTime.now().toString().replace(":", "_") + ".csv"));
        BufferedWriter fileToSave = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("D:\\CP\\ContentWeb\\" + host + "-" + LocalDateTime.now().toString().replace(":", "_") + ".csv"), "cp1252"));
        fileToSave.append("TITLE;LINK OFFER;IMAGES;OWNER;TEL;LOCALITY;REGION;\n");
        int loop = 0;
        while (continueCrawl) {
            loop++;
            Page listingPage = getConnection(entryPoint, "listing");
            System.out.println();
            System.out.println("Page : " + loop);
            if (loop == 1) {
                Document listingDocument = Jsoup.parse(listingPage.getContent());
                Elements offers = listingDocument.select("div.listing-face");
                System.out.println("Offers per page : " + offers.size());
                int offerIndex = 0;
                for (Element offer : offers) {
                    offerIndex++;
                    System.out.println();
                    System.out.println("Offer : " + offerIndex);

                    String linkOffer = getLinkOffer(offer);
                    System.out.println("Link : " + linkOffer);

                    fileToSave.append(crawlOfferPage(linkOffer));
                }

                Element nextPageElement = listingDocument.select("div.pager-right li.disabled > a.js-nextPage").first();
                continueCrawl = nextPageElement == null;
            } else {
                JsonNode listingNode = new ObjectMapper().readTree(listingPage.getContent());
                Integer pageCount = listingNode.path("results").path("pageCount").asInt();
                JsonNode offerNode = listingNode.path("results").path("hits");
                for (int offerIndex = 0; offerIndex < offerNode.size(); offerIndex++) {
                    System.out.println();
                    System.out.println("Offer : " + offerIndex);

                    String linkOffer = cleanPath(offerNode.path(offerIndex).path("detailPageUrl").asText());
                    System.out.println("Link : " + linkOffer);

                    fileToSave.append(crawlOfferPage(linkOffer));
                }
                continueCrawl = loop <= pageCount;
            }

            entryPoint = "https://www.homelidays.com/ajax/map/search/israel/region:774/arrival:2016-12-30/departure:2017-01-31/@,,,,z/page:" + loop + "?view=l";
        }
        fileToSave.close();
        httpClient.getConnectionManager().shutdown();
    }

    private static String crawlOfferPage(String linkOffer) {
        Page offerPage = getConnection(linkOffer, "offer");
        Document offerDocument = Jsoup.parse(offerPage.getContent());

        String title = offerDocument.select("h1.property-title").first().text();
        title = title.replace(";", ",");
        System.out.println("Title : " + title);

        String images = getImage(offerPage.getContent());
        System.out.println("Images : " + images);

        String owner = offerDocument.select("span.owner-name").text();
        System.out.println("Owner : " + owner);

        String tel = offerDocument.select("div.tel").text();
        System.out.println("Tel : " + tel);

        String locality = offerDocument.select("div.adr > span.locality").text();
        System.out.println("Locality : " + locality);

        String region = offerDocument.select("div.adr > span.region").text();
        System.out.println("Region : " + region);

        return title + ";" + linkOffer + ";" + images + ";" + owner + ";" + tel + ";" + locality + ";" + region + ";\n";
    }

    private static String getImage(String offer) {
        String images = "";
        String contentOffer = StringUtils.substringBetween(offer, "define('pageData', [],", ");");
        try {
            JsonNode offerObject = new ObjectMapper().readTree(contentOffer);
            JsonNode imageNode = offerObject.path("listing").path("images");
            for (int indexImage = 0; indexImage < imageNode.size(); indexImage++) {
                String image = imageNode.path(indexImage).path("imageFiles").path(0).path("uri").asText();
                images += cleanPath(image) + ",";
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return images;
    }

    private static String getLinkOffer(Element offer) {
        Element linkElement = offer.select("h3.hit-headline > a").first();
        String link = linkElement.attr("href");
        return cleanPath(link);
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
        DefaultHttpClient client = new DefaultHttpClient();
        HttpHost proxyHost = new HttpHost("222.124.203.177", 8800);

        HttpRoutePlanner routePlanner = new HttpRoutePlanner() {
            @Override
            public HttpRoute determineRoute(HttpHost target, HttpRequest request, HttpContext context) throws HttpException {
                return new HttpRoute(target, null, new HttpHost("222.124.203.177", 8800), "https".equalsIgnoreCase(target.getSchemeName()));
            }
        };

        client.getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 30000);
        client.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, 60000);
        client.getParams().setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.BROWSER_COMPATIBILITY);
        client.getParams().setParameter(ConnRouteParams.DEFAULT_PROXY, proxyHost);
        client.setRoutePlanner(routePlanner);
        return client;
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
