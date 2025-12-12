package cn.bugstack.xfg.dev.tech.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author zjh
 * @since 2025/12/12 09:16
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Response<T> implements Serializable {

    private String info;
    private String code;
    private T data;

}