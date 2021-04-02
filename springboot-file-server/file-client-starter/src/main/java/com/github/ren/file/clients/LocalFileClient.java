package com.github.ren.file.clients;

import com.github.ren.file.properties.LocalFileProperties;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.util.List;

/**
 * 本地存储实现类
 */
public class LocalFileClient extends AbstractServerClient implements LocalClient {

    private final LocalFileProperties localFileProperties;

    public LocalFileClient(LocalFileProperties localFileProperties) {
        this.localFileProperties = localFileProperties;
    }

    public File getOutFile(String yourObjectName) {
        String relativePath = Paths.get(localFileProperties.getFileStoragePath(), yourObjectName).toString();
        File fileDir = new File(relativePath).getParentFile();
        if (!fileDir.exists() && !fileDir.mkdirs()) {
            throw new RuntimeException("local mkdirs error");
        }
        return new File(relativePath);
    }

    @Override
    public String getWebServerUrl() {
        return localFileProperties.getWebServerUrl();
    }

    @Override
    public String uploadFile(File file, String yourObjectName) {
        File outFile = this.getOutFile(yourObjectName);
        try {
            super.copyFile(file, outFile);
        } catch (IOException e) {
            throw new FileIOException(e);
        }
        return outFile.getName();
    }

    @Override
    public String uploadFile(InputStream inputStream, String yourObjectName) {
        File outFile = this.getOutFile(yourObjectName);
        try {
            super.copyFile(inputStream, outFile);
        } catch (IOException e) {
            throw new FileIOException(e);
        }
        return outFile.getName();
    }

    @Override
    public String uploadFile(byte[] content, String yourObjectName) {
        File outFile = this.getOutFile(yourObjectName);
        try (ByteArrayInputStream is = new ByteArrayInputStream(content)) {
            this.copyFile(is, outFile);
        } catch (IOException e) {
            throw new FileIOException(e);
        }
        return outFile.getName();
    }

    @Override
    public String uploadPart(List<File> files, String yourObjectName) {
        File outFile = this.getOutFile(yourObjectName);
        try (FileChannel outChannel = new FileOutputStream(outFile).getChannel()) {
            //同步nio 方式对分片进行合并, 有效的避免文件过大导致内存溢出
            for (File file : files) {
                long chunkSize = 1L << 32;
                if (file.length() >= chunkSize) {
                    throw new RuntimeException("文件分片必须<4G");
                }
                try (FileChannel inChannel = new FileInputStream(file).getChannel()) {
                    int position = 0;
                    long size = inChannel.size();
                    while (0 < size) {
                        long count = inChannel.transferTo(position, size, outChannel);
                        if (count > 0) {
                            position += count;
                            size -= count;
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new FileIOException(e);
        }
        return outFile.getName();
    }

    @Override
    public void deleteFile(String filepath) {
        FileUtils.deleteQuietly(new File(filepath));
    }
}
