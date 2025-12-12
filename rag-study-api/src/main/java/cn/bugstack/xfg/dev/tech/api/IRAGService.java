package cn.bugstack.xfg.dev.tech.api;

import cn.bugstack.xfg.dev.tech.api.response.Response;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @author zjh
 * @since 2025/12/12 09:18
 */
public interface IRAGService {

    Response<List<String>> queryRagTagList();

    Response<String> uploadFiles(String ragTag, List<MultipartFile> files);

}
