package org.mage.plugins.card.utils;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.HashMap;
import java.util.Objects;
import java.util.prefs.Preferences;
import mage.client.MageFrame;
import mage.client.constants.Constants;
import mage.client.dialog.PreferencesDialog;
import mage.remote.Connection;
import mage.remote.Connection.ProxyType;
import net.java.truevfs.access.TFile;
import org.apache.log4j.Logger;
import org.mage.plugins.card.images.CardDownloadData;
import org.mage.plugins.card.properties.SettingsManager;

public final class CardImageUtils {

    private static final HashMap<CardDownloadData, String> pathCache = new HashMap<>();
    private static final Logger log = Logger.getLogger(CardImageUtils.class);

    /**
     *
     * @param card
     * @return String if image exists, else null
     */
    public static String generateTokenImagePath(CardDownloadData card) {
        if (card.isToken()) {
            String filePath = getTokenImagePath(card);
            if (pathCache.containsKey(card)) {
                if (filePath.equals(pathCache.get(card))) {
                    return pathCache.get(card);
                }
            }
            TFile file = new TFile(filePath);

            if (!file.exists() && card.getTokenSetCode() != null) {
                filePath = searchForCardImage(card);
                file = new TFile(filePath);
            }

            if (file.exists()) {
                pathCache.put(card, filePath);
                return filePath;
            }
        }
        log.warn("Token image file not found: " + card.getSet() + " - " + card.getTokenSetCode() + " - " + card.getName());
        return null;
    }

    /**
     *
     * @param card
     * @return String regardless of whether image exists
     */
    public static String generateFullTokenImagePath(CardDownloadData card) {
        if (card.isToken()) {
            String filePath = getTokenImagePath(card);
            return filePath;
        }
        return "";
    }

    private static String getTokenImagePath(CardDownloadData card) {
        String filename = buildImagePathToCard(card);

        TFile file = new TFile(filename);
        if (!file.exists()) {
            filename = generateTokenDescriptorImagePath(card);
        }

        file = new TFile(filename);
        if (!file.exists()) {
            CardDownloadData updated = new CardDownloadData(card);
            updated.setName(card.getName() + " 1");
            filename = buildImagePathToCard(updated);
            file = new TFile(filename);
            if (!file.exists()) {
                updated = new CardDownloadData(card);
                updated.setName(card.getName() + " 2");
                filename = buildImagePathToCard(updated);
            }
        }

        return filename;
    }

    private static String searchForCardImage(CardDownloadData card) {
        TFile file;
        String path;
        CardDownloadData c = new CardDownloadData(card);
        c.setSet(card.getTokenSetCode());
        path = getTokenImagePath(c);
        file = new TFile(path);
        if (file.exists()) {
            pathCache.put(card, path);
            return path;
        }

//        for (String set : SettingsManager.getIntance().getTokenLookupOrder()) {
//            c.setSet(set);
//            path = getTokenImagePath(c);
//            file = new TFile(path);
//            if (file.exists()) {
//                pathCache.put(card, path);
//                return path;
//            }
//        }
        return generateTokenDescriptorImagePath(card);
    }

    public static String updateSet(String cardSet, boolean forUrl) {
        String set = cardSet.toLowerCase();
        if (set.equals("con")) {
            set = "cfx";
        }
        if (forUrl) {
            set = SettingsManager.getIntance().getSetNameReplacement(set);
        }
        return set;
    }

    public static String prepareCardNameForFile(String cardName){
        return cardName.replace(":", "").replace("\"", "").replace("//", "-");
    }

    public static String getImagesDir(){
        // return real images dir (path without separator)

        String path = null;

        // user path
        if (!PreferencesDialog.getCachedValue(PreferencesDialog.KEY_CARD_IMAGES_USE_DEFAULT, "true").equals("true")){
            path = PreferencesDialog.getCachedValue(PreferencesDialog.KEY_CARD_IMAGES_PATH, null);
        }

        // default path
        if (path == null) {
            path = Constants.IO.DEFAULT_IMAGES_DIR;
        }

        while(path.endsWith(File.separator))
        {
            path = path.substring(0, path.length() - 1);
        }

        return path;
    }

    public static String buildImagePathToTokens() {
        String imagesPath = getImagesDir() + File.separator;


        if (PreferencesDialog.isSaveImagesToZip()) {
            return imagesPath + "TOK.zip" + TFile.separator;
        } else {
            return imagesPath + "TOK" + TFile.separator;
        }
    }

    private static String buildImagePathToTokenDescriptor(CardDownloadData card) {
        return buildImagePathToTokens() + card.getTokenDescriptor() + ".full.jpg";
    }

    public static String buildImagePathToSet(CardDownloadData card) {

        if (card.getSet() == null) {
            throw new IllegalArgumentException("Card " + card.getName() + " have empty set.");
        }

        String set = updateSet(card.getSet(), false).toUpperCase(); // TODO: research auto-replace... old code?

        if (card.isToken()) {
            return buildImagePathToSetAsToken(set);
        } else {
            return buildImagePathToSetAsCard(set);
        }
    }

    private static String buildImagePathToSetAsCard(String set) {
        String imagesPath = getImagesDir() + File.separator;

        if (PreferencesDialog.isSaveImagesToZip()) {
            return imagesPath + set + ".zip" + File.separator;
        } else {
            return imagesPath + set + File.separator;
        }
    }

    private static String buildImagePathToSetAsToken(String set) {
        return buildImagePathToTokens() + set + File.separator;
    }

    public static String buildImagePathToCard(CardDownloadData card) {

        String setPath = buildImagePathToSet(card);

        String prefixType = "";
        if(card.getType() != 0){
            prefixType = " " + Integer.toString(card.getType());
        }

        String cardName = card.getFileName();
        if (cardName.isEmpty()){
            cardName = prepareCardNameForFile(card.getName());
        }

        String finalFileName = "";
        if (card.getUsesVariousArt()){
            finalFileName = cardName + '.' + card.getCollectorId() + ".full.jpg";
        }else{
            finalFileName = cardName + prefixType + ".full.jpg";
        }

        // if image file exists, correct name (for case sensitive systems)
        // use TFile for zips
        TFile dirFile = new TFile(setPath);
        TFile imageFile = new TFile(setPath + finalFileName);
        // warning, zip files can be broken
        try{
            if (dirFile.exists() && !imageFile.exists()) {
                // search like names
                for (String fileName: dirFile.list()) {
                    if (fileName.toLowerCase().equals(finalFileName.toLowerCase())) {
                        finalFileName = fileName;
                        break;
                    }
                }
            }
        }catch (Exception ex) {
            log.error("Can't read card name from file, may be it broken: " + setPath);
        }

        return setPath + finalFileName;
    }

    public static String generateFaceImagePath(String cardname, String set) {
        return getImagesDir() + File.separator + "FACE" + File.separator + set + File.separator + prepareCardNameForFile(cardname) + File.separator + ".jpg";
    }

    public static String generateTokenDescriptorImagePath(CardDownloadData card) {

        String straightImageFile = buildImagePathToTokenDescriptor(card);
        TFile file = new TFile(straightImageFile);
        if (file.exists()) {
            return straightImageFile;
        }

        straightImageFile = straightImageFile.replaceFirst("\\.[0-9]+\\.[0-9]+", ".X.X");
        file = new TFile(straightImageFile);
        if (file.exists()) {
            return straightImageFile;
        }

        straightImageFile = straightImageFile.replaceFirst("\\.X\\.X", ".S.S");
        file = new TFile(straightImageFile);
        if (file.exists()) {
            return straightImageFile;
        }
        return "";
    }

    public static Proxy getProxyFromPreferences() {
        Preferences prefs = MageFrame.getPreferences();
        Connection.ProxyType proxyType = Connection.ProxyType.valueByText(prefs.get("proxyType", "None"));
        if (proxyType != ProxyType.NONE) {
            String proxyServer = prefs.get("proxyAddress", "");
            int proxyPort = Integer.parseInt(prefs.get("proxyPort", "0"));
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyServer, proxyPort));
        }
        return null;
    }
}
