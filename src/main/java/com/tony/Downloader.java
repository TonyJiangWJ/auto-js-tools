package com.tony;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * @author jiangwenjie 2019/11/11
 */
public class Downloader {

    private String targetReleasesApiUrl;
    private String outputDir;
    private HttpURLConnection urlConnection;
    private List<String> unzipSkipFiles;
    private List<String> backupIgnoreFiles;
    private JSONObject latestObj;
    private DownloaderListener listener;
    private ZipUtil zipUtil;
    /**
     * 尝试获取content-length的次数
     */
    private int tryCount;

    public Downloader() {
        tryCount = 5;
    }

    public JSONObject getLatestInfo() {
        return getLatestInfo(false);
    }

    public JSONObject getLatestInfo(boolean forceUpdate) {
        if (forceUpdate || latestObj == null) {
            ByteArrayOutputStream byteArrayOutputStream = null;
            InputStream inputStream = null;
            try {
                urlConnection = (HttpURLConnection)new URL(targetReleasesApiUrl).openConnection();
                //设置连接时间，10秒
                urlConnection.setConnectTimeout(10 * 1000);
                urlConnection.setReadTimeout(10 * 1000);

                //数据编码格式，这里utf-8
                urlConnection.setRequestProperty("Charset", "utf-8");

                //设置返回结果的类型，这里是json
                urlConnection.setRequestProperty("accept", "application/json");

                //这里设置post传递的内容类型，这里json
                urlConnection.setRequestProperty("Content-Type", "application/json");

                System.setProperty("https.protocols", "TLSv1.2");
                urlConnection.connect();
                byteArrayOutputStream = new ByteArrayOutputStream();
                if (urlConnection.getResponseCode() == 200) {
                    inputStream = urlConnection.getInputStream();
                    byte[] buff = new byte[1024];
                    int l = 0;
                    while ((l = inputStream.read(buff)) > 0) {
                        byteArrayOutputStream.write(buff, 0, l);
                    }
                    byte[] contentBytes = byteArrayOutputStream.toByteArray();
                    String result = new String(contentBytes, StandardCharsets.UTF_8);
                    System.out.println(result);

                    latestObj = new JSONObject(new JSONTokener(result));
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                finallyCloseStreams(inputStream, byteArrayOutputStream);
            }
        }
        return latestObj;
    }

    public String getUpdateSummary() {
        JSONObject updateInfo = getLatestInfo();
        if (updateInfo != null) {
            String body = updateInfo.getString("body");
            String nodeId = updateInfo.getString("node_id");
            String tagName = updateInfo.getString("tag_name");
            JSONObject summary = new JSONObject();
            summary.put ("body", body);
            summary.put("nodeId",  nodeId);
            summary.put("tagName", tagName);
            return summary.toString();
        }
        return null;
    }

    private String getZipDownloadUrl() {
        JSONObject resultObj = getLatestInfo();
        if (resultObj != null) {
            String downUrl = resultObj.getString("zipball_url");
            getListener().updateGui("download zip url: " + downUrl);
            return downUrl;
        }
        return null;
    }


    private HttpURLConnection createDownloadConnection(String url) throws IOException {
        urlConnection = (HttpURLConnection)new URL(url).openConnection();
        //设置连接时间，100秒
        urlConnection.setConnectTimeout(100 * 1000);
        urlConnection.setReadTimeout(100 * 1000);

        //数据编码格式，这里utf-8
        urlConnection.setRequestProperty("Charset", "utf-8");
        urlConnection.setRequestProperty("Accept-Encoding", "identity");
        urlConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/78.0.3904.97 Safari/537.36");
        urlConnection.connect();
        return urlConnection;
    }

    private double tryGetConnectionWithLength(String url, int tryCount) throws IOException {
        createDownloadConnection(url);
        double totalLength = urlConnection.getContentLength();
        int triedTime = 1;
        showTrySummary(triedTime, totalLength);
        while (totalLength < 0 && triedTime++ < tryCount) {
            urlConnection.disconnect();
            createDownloadConnection(url);
            totalLength = urlConnection.getContentLength();
            showTrySummary(triedTime, totalLength);
        }
        return totalLength;
    }

    private void showTrySummary(int triedTime, double length) {
        getListener().updateGui("第" + triedTime + "次尝试获取http总大小：" + length);
    }

    public void downloadZip() {
        String zipUrl = getZipDownloadUrl();
        if (zipUrl != null) {

            ByteArrayOutputStream byteArrayOutputStream = null;
            InputStream inputStream = null;
            try {
                double totalLength = tryGetConnectionWithLength(zipUrl, tryCount);
                getListener().updateGui("最终获取http总大小：" + totalLength);
//                getListener().updateGui("transfer encoding:" + urlConnection.getHeaderField("Transfer-Encoding"));

                byteArrayOutputStream = new ByteArrayOutputStream();
                inputStream = urlConnection.getInputStream();
                if (inputStream == null) {
                    getListener().updateGui("无可下载内容");
                    return;
                }

                int l = -1;
                byte[] buff = new byte[1024];
                int readLength = 0;
                int readCount = 0;
                String content = null;
                boolean showRealProgress = totalLength > 0;
                while ((l = inputStream.read(buff)) > 0) {
                    readLength += l;
                    readCount++;
                    content = null;
                    // 每5次更新进度数据
                    boolean displayProgress = readCount % 5 == 0;
                    if (readCount == 1 && !showRealProgress) {
                        content = "未能获取文件总大小，请等待下载完成.";
                    }
                    if (displayProgress) {
                        if (showRealProgress) {
                            content = String.format("下载进度：%.2f%%", ((readLength / totalLength) * 100));
                            getListener().updateProgress(new ProgressInfo((int)totalLength, readLength));
                        } else {
                            // 模拟进度
                            int mockTotal = readLength + 10 * 1024;
                            getListener().updateProgress(new ProgressInfo(mockTotal, readLength));
                        }
                    }
                    if (content != null) {
                        getListener().updateGui(content);
                    }

                    byteArrayOutputStream.write(buff, 0, l);
                }

                // 下载完毕后 更新进度为百分百
                getListener().updateProgress(new ProgressInfo(10, 10));

                byte[] dataContent = byteArrayOutputStream.toByteArray();
                getListener().updateGui("数据总长度：" + dataContent.length);
                unzipBytes(dataContent);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                finallyCloseStreams(inputStream, byteArrayOutputStream);
            }
        }
    }

    private void unzipBytes(byte[] dataContent) {
        if (dataContent != null && dataContent.length > 0) {

            File file = saveBytesToFile(dataContent);
            unzipFile(file);
        }
    }


    private File saveBytesToFile(byte[] dataContent) {
        File tmpFile = new File(outputDir + "/origin.zip");
        boolean dirExist = tmpFile.getParentFile().exists();
        if (!dirExist) {
            dirExist = tmpFile.getParentFile().mkdirs();
        }
        if (dirExist) {
            getListener().updateGui("保存到临时文件：" + tmpFile.getAbsolutePath());
            FileOutputStream fileOutputStream = null;
            try {
                fileOutputStream = new FileOutputStream(tmpFile);
                fileOutputStream.write(dataContent);
                fileOutputStream.flush();
                return tmpFile;
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                finallyCloseStreams(fileOutputStream);
            }
        }
        return null;
    }

    private void unzipFile(File file) {
        if (getZipUtil().decompress(file, outputDir)) {
            this.setLocalVersion();
            getListener().updateGui("解压成功！");
        } else {
            getListener().updateGui("解压失败！");
        }
    }


    public boolean backup() {
        String localVersionName = this.getLocalVersion();
        String backupFileName = null;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHH");
        String dateStr = sdf.format(new Date());
        if (localVersionName == null) {
            backupFileName = "未知版本号-" + dateStr + ".zip";
        } else {
            backupFileName = localVersionName + "-" + dateStr + ".zip";
        }
        File backupFile = new File(outputDir + "/backup_zip/" + backupFileName);
        boolean backDstPathExist = true;
        if (!backupFile.getParentFile().exists()) {
            backDstPathExist = backupFile.getParentFile().mkdirs();
        }
        if (backDstPathExist && backupFile.getParentFile().isDirectory()) {
            return getZipUtil().compress(outputDir, backupFile.getPath());
        } else {
            getListener().updateGui("备份文件夹不存在:" + backupFile.getParent());
        }
        return false;
    }

    public String getLocalVersion() {
        String versionFilePath = this.outputDir + "/version.json";
        File versionFile = new File(versionFilePath);
        if (versionFile.exists()) {
            try (
                    FileInputStream fileInputStream = new FileInputStream(versionFile);
                    BufferedReader buffReader = new BufferedReader(new InputStreamReader(fileInputStream))
            ) {
                String versionContent = buffReader.readLine();
                if (versionContent != null) {
                    try {

                        JSONObject jsonObject = new JSONObject(new JSONTokener(versionContent));
                        String version = jsonObject.getString("version");
                        String nodeId = jsonObject.getString("nodeId");
                        getListener().updateGui("本地版本：" + version + " nodeId:" + nodeId);
                        return version;
                    } catch (Exception e) {
                        getListener().updateGui("解析版本文件异常" + e.getMessage());
                    }
                }

            } catch (IOException e) {
                getListener().updateGui("读取版本文件异常" + e.getMessage());
            }
        } else {
            getListener().updateGui("版本文件不存在");
        }
        return null;
    }


    private DownloaderListener getListener() {
        if (listener == null) {
            listener = new DefaultDownloaderListener();
        }
        return listener;
    }

    private ZipUtil getZipUtil() {
        if (zipUtil == null) {
            zipUtil = new ZipUtil();
        }
        zipUtil.setListener(getListener());
        zipUtil.setUnzipSkipFiles(unzipSkipFiles);
        zipUtil.setZipIgnoredFiles(getBackupIgnoreFiles("/backup_zip", ".DS_Store"));
        return zipUtil;
    }


    private List<String> getBackupIgnoreFiles(String... addIfNones) {
        if (addIfNones != null && addIfNones.length > 0) {
            for (String addIfNone : addIfNones) {
                addBackIgnoresIfNone(addIfNone);
            }
        }
        return backupIgnoreFiles;
    }

    private void addBackIgnoresIfNone(String addIfNone) {
        boolean needAdd = false;
        if (backupIgnoreFiles == null) {
            backupIgnoreFiles = new ArrayList<>();
            needAdd = true;
        } else if (!backupIgnoreFiles.contains(addIfNone)) {
            needAdd = true;
        }
        if (needAdd) {
            backupIgnoreFiles.add(addIfNone);
        }
    }

    private void finallyCloseStreams(Closeable... streams) {
        if (streams != null && streams.length > 0) {
            for (Closeable stream : streams) {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void setLocalVersion() {
        JSONObject latestInfo = this.getLatestInfo();
        if (latestInfo == null || latestInfo.getString("tag_name") == null) {
            getListener().updateGui("无法获取最新版本信息");
            return;
        }
        String versionFilePath = this.outputDir + "/version.json";
        File versionFile = new File(versionFilePath);
        try (
                FileOutputStream fileOutputStream = new FileOutputStream(versionFile);
                BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(fileOutputStream));
        ) {
            JSONObject versionInfo = new JSONObject();
            versionInfo.put("version", latestInfo.get("tag_name"));
            versionInfo.put("nodeId", latestInfo.get("node_id"));
            bufferedWriter.write(versionInfo.toString());
            bufferedWriter.flush();
            getListener().updateGui("设置本地版本信息：" + versionInfo.toString());
        } catch (Exception e) {
            getListener().updateGui("更新版本文件异常");
        }
    }


    public void setListener(DownloaderListener listener) {
        this.listener = listener;
    }

    public List<String> getUnzipSkipFiles() {
        return unzipSkipFiles;
    }

    public void setUnzipSkipFiles(String... unzipSkipFiles) {
        if (unzipSkipFiles != null && unzipSkipFiles.length > 0) {
            this.unzipSkipFiles = new ArrayList<>(Arrays.asList(unzipSkipFiles));
        }
    }

    public void setBackupIgnoreFiles(String... backupIgnoreFiles) {
        if (backupIgnoreFiles != null && backupIgnoreFiles.length > 0) {
            this.backupIgnoreFiles = new ArrayList<>(Arrays.asList(backupIgnoreFiles));
        }
    }

    public String getTargetReleasesApiUrl() {
        return targetReleasesApiUrl;
    }

    public void setTargetReleasesApiUrl(String targetReleasesApiUrl) {
        this.targetReleasesApiUrl = targetReleasesApiUrl;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public int getTryCount() {
        return tryCount;
    }

    public void setTryCount(int tryCount) {
        this.tryCount = tryCount;
    }

    public static void main(String[] args) throws IOException {
        String apiUrl = "https://api.github.com/repos/TonyJiangWJ/Ant-Forest-autoscript/releases/latest";
        String targetOutputDir = "/Users/jiangwenjie/Documents/Repositories/Github/github-releases-downloader/target/ntes";
        Downloader downloader = new Downloader();
        downloader.setTargetReleasesApiUrl(apiUrl);
        downloader.setOutputDir(targetOutputDir);
        downloader.setListener(new DefaultDownloaderListener());
        downloader.setUnzipSkipFiles("config.js", ".gitignore");
        downloader.setBackupIgnoreFiles("config.js", ".gitignore");
        downloader.setTryCount(0);
        downloader.backup();
//        System.out.println(downloader.getLocalVersion());
        System.out.println(downloader.getUpdateSummary());
        downloader.downloadZip();
//        downloader.unzipFile(new File("/Users/jiangwenjie/Documents/Repositories/Github/github-releases-downloader/target/origin.zip"));
    }
}
