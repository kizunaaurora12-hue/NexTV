package com.nextv.app.data;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 * Model untuk satu channel TV.
 * Mendukung HLS, DASH, dan DASH+DRM (Widevine/ClearKey).
 */
public class Channel {

    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("url")
    private String url;

    @SerializedName("logo")
    private String logo;

    @SerializedName("category")
    private String category;

    @SerializedName("number")
    private int number;

    @SerializedName("quality")
    private String quality;  // SD, HD, FHD, 4K

    @SerializedName("epg_id")
    private String epgId;

    @SerializedName("is_live")
    private boolean isLive;

    /** "widevine" atau "clearkey" – kosong/null berarti tidak ada DRM */
    @SerializedName("drm_scheme")
    private String drmScheme;

    /** URL license server Widevine */
    @SerializedName("drm_license_url")
    private String drmLicenseUrl;

    /** ClearKey: key ID (hex) */
    @SerializedName("drm_key_id")
    private String drmKeyId;

    /** ClearKey: key value (hex) */
    @SerializedName("drm_key")
    private String drmKey;

    /** Header tambahan untuk request stream, misal {"Referer":"..."} */
    @SerializedName("headers")
    private Map<String, String> headers;

    public Channel() {}

    public Channel(String id, String name, String url, String category) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.category = category;
        this.quality = "HD";
        this.isLive = true;
    }

    public String getId()                          { return id; }
    public void   setId(String id)                 { this.id = id; }

    public String getName()                        { return name != null ? name : ""; }
    public void   setName(String name)             { this.name = name; }

    public String getUrl()                         { return url != null ? url : ""; }
    public void   setUrl(String url)               { this.url = url; }

    public String getLogo()                        { return logo != null ? logo : ""; }
    public void   setLogo(String logo)             { this.logo = logo; }

    public String getCategory()                    { return category != null ? category : "Umum"; }
    public void   setCategory(String category)     { this.category = category; }

    public int    getNumber()                      { return number; }
    public void   setNumber(int number)            { this.number = number; }

    public String getQuality()                     { return quality != null ? quality : "SD"; }
    public void   setQuality(String quality)       { this.quality = quality; }

    public String getEpgId()                       { return epgId; }
    public void   setEpgId(String epgId)           { this.epgId = epgId; }

    public boolean isLive()                        { return isLive; }
    public void    setLive(boolean live)           { isLive = live; }

    public String getDrmScheme()                   { return drmScheme; }
    public void   setDrmScheme(String s)           { this.drmScheme = s; }

    public String getDrmLicenseUrl()               { return drmLicenseUrl; }
    public void   setDrmLicenseUrl(String u)       { this.drmLicenseUrl = u; }

    public String getDrmKeyId()                    { return drmKeyId; }
    public void   setDrmKeyId(String k)            { this.drmKeyId = k; }

    public String getDrmKey()                      { return drmKey; }
    public void   setDrmKey(String k)              { this.drmKey = k; }

    public Map<String, String> getHeaders()        { return headers; }
    public void setHeaders(Map<String, String> h)  { this.headers = h; }

    /** Cek apakah channel ini butuh DRM */
    public boolean hasDrm() {
        return drmScheme != null && !drmScheme.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "Channel{name='" + name + "', url='" + url + "', drm='" + drmScheme + "'}";
    }
}
