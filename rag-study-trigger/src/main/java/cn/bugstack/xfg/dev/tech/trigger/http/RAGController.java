package cn.bugstack.xfg.dev.tech.trigger.http;

import cn.bugstack.xfg.dev.tech.api.IRAGService;
import cn.bugstack.xfg.dev.tech.api.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zjh
 * @since 2025/12/12 09:27
 */
@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/rag/")
public class RAGController implements IRAGService {

    @Resource
    private RedissonClient redissonClient;
    @Resource
    private PgVectorStore pgVectorStore;
    @Resource
    private TokenTextSplitter tokenTextSplitter;

    @GetMapping("query_rag_tag_list")
    @Override
    public Response<List<String>> queryRagTagList() {
        RList<String> elements = redissonClient.getList("ragTag");
        return Response.<List<String>>builder()
                .code("0000")
                .info("调用成功")
                .data(new ArrayList<>(elements))
                .build();
    }

    @PostMapping(value = "file/upload", headers = "content-type=multipart/form-data")
    @Override
    public Response<String> uploadFiles(
            @RequestParam String ragTag,
            @RequestParam("file") List<MultipartFile> files
    ) {
        log.info("上传知识库开始... :{}", ragTag);

        for (MultipartFile file : files) {
            // 读取文件
            TikaDocumentReader documentReader = new TikaDocumentReader(file.getResource());
            List<Document> documentList = documentReader.get();
            // 拆分文本
            List<Document> documentsSplitterList = tokenTextSplitter.apply(documentList);
            // 标记文本数据
            documentsSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", ragTag));
            // 存进向量库
            pgVectorStore.accept(documentsSplitterList);

            // 更新redis数据
            RList<String> elements = redissonClient.getList("ragTag");
            for (String element : elements) {
                if(!element.contains(ragTag)) {
                    elements.add(ragTag);
                }
            }
        }

        log.info("上传知识库完成... :{}", ragTag);
        return Response.<String>builder()
                .code("0000")
                .info("调用成功")
                .build();
    }
}