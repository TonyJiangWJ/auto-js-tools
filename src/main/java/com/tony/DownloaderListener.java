package com.tony;

/**
 * @author jiangwenjie 2019/11/12
 */
public interface DownloaderListener {

    /**
     * 更新文本内容
     *
     * @param string 要展示的内容
     */
    void updateGui(String string);

    /**
     * 更新进度信息
     *
     * @param progressInfo 进度数据
     */
    void updateProgress(ProgressInfo progressInfo);
}
