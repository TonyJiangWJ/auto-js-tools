package com.tony;


import org.json.JSONStringer;

/**
 * @author jiangwenjie 2019/11/22
 */
public class DefaultDownloaderListener implements DownloaderListener {
    @Override
    public void updateGui(String string) {
        System.out.println(string);
    }

    @Override
    public void updateProgress(ProgressInfo progressInfo) {
        System.out.println("更新进度：" + JSONStringer.valueToString(progressInfo) + String.format(" %.2f%%", progressInfo.getProgress()));
    }
}
