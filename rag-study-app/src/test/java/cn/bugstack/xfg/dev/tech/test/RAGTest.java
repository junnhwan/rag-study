package cn.bugstack.xfg.dev.tech.test;

import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author zjh
 * @since 2025/12/11 20:33
 */
@Slf4j
@SpringBootTest
@RunWith(SpringRunner.class)
public class RAGTest {

    @Resource
    private OllamaChatClient ollamaChatClient;
    @Resource
    private TokenTextSplitter tokenTextSplitter;
    @Resource
    private SimpleVectorStore simpleVectorStore;
    @Resource
    private PgVectorStore pgVectorStore;

    @Test
    public void upload() {
        // 1. 文件解析：初始化文档读取器并指定读取文件路径
        TikaDocumentReader reader = new TikaDocumentReader("./data/info.txt");

        List<Document> documents = reader.get();
        // 2. 文本拆分：进行分词 chunking 操作
        List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

        // 3. 文本标记：给数据打标签
        documents.forEach(doc -> doc.getMetadata().put("knowledge", "知识库名称"));
        documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", "知识库名称"));

        // 4. 向量化存储：存储数据到向量数据库
        pgVectorStore.accept(documentSplitterList);
        // 打印日志
        log.info("上传完成...");
    }

    /**
     * upload(): 文档上传（解析→拆分→向量化→存储到 PgVector）
     * → chat(): 用户提问→向量数据库检索相关文档→拼接上下文→AI 生成精准回答
     */
    @Test
    public void chat() {
        // 1. 用户问题 和 系统提示词
        String message = "张三，哪年出生";

        String SYSTEM_PROMPT = """
                Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
                If unsure, simply state that you don't know.
                Another thing you need to note is that your reply must be in Chinese!
                DOCUMENTS:
                    {documents}
                """;

        // 2. 构建检索请求
        SearchRequest request = SearchRequest
                .query(message)
                .withFilterExpression("knowledge == '知识库名称'")
                .withTopK(5);  // 检索5个最相关的数据

        // 3. 数据库根据请求检索相关数据
        List<Document> documents = pgVectorStore.similaritySearch(request);

        // 4. 把数据库检索的信息拼接成字符串方便AI处理
        String documentsCollectors = documents.stream()
                .map(Document::getContent)
                .collect(Collectors.joining());

        // 5. 把系统提示词和向量数据库检索出的文档结合成系统消息
        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT)
                .createMessage(Map.of("documents", documentsCollectors));

        // 6. 构建消息列表，即把刚刚构建的系统信息（收集的相关数据）和用户问题结合成将要喂给AI的提示词
        ArrayList<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(message));
        messages.add(ragMessage);

        // 7. 调用AI生成回答，把消息给AI客户端
        ChatResponse chatResponse = ollamaChatClient.call(new Prompt(
                messages,
                OllamaOptions.create().withModel("deepseek-r1:1.5b")
                )
        );

        // 8. 打印最终结果
        log.info("测试结果：{}", JSON.toJSONString(chatResponse));
    }

}