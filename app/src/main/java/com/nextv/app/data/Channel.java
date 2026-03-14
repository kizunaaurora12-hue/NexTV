package com.nextv.app.data;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Model untuk satu channel TV.
 * Format JSON disesuaikan dengan channels.json dari SymphogearTV.
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

    // Constructors
    public Channel() {}

    public Channel(String id, String name, String url, String category) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.category = category;
        this.quality = "HD";
        this.isLive = true;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name != null ? name : ""; }
    public void setName(String name) { this.name = name; }

    public String getUrl() { return url != null ? url : ""; }
    public void setUrl(String url) { this.url = url; }

    public String getLogo() { return logo != null ? logo : ""; }
    public void setLogo(String logo) { this.logo = logo; }

    public String getCategory() { return category != null ? category : "Umum"; }
    public void setCategory(String category) { this.category = category; }

    public int getNumber() { return number; }
    public void setNumber(int number) { this.number = number; }

    public String getQuality() { return quality != null ? quality : "SD"; }
    public void setQuality(String quality) { this.quality = quality; }

    public String getEpgId() { return epgId; }
    public void setEpgId(String epgId) { this.epgId = epgId; }

    public boolean isLive() { return isLive; }
    public void setLive(boolean live) { isLive = live; }

    @Override
    public String toString() {
        return "Channel{name='" + name + "', url='" + url + "', category='" + category + "'}";
    }
}
