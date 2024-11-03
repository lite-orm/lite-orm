package template;

import com.fizzed.rocker.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author 王洋洋
 * @since 创建于 2024/11/01 21:25
 */
@EqualsAndHashCode()
@Data
public class User implements RockerModel {
    private Integer id;
    private String name;
    private String email;

    @Override
    public RockerOutput render() throws RenderingException {
        return null;
    }

    @Override
    public <O extends RockerOutput> O render(RockerOutputFactory<O> outputFactory) throws RenderingException {
        return null;
    }

    @Override
    public <O extends RockerOutput> O render(RockerOutputFactory<O> outputFactory, RockerTemplateCustomizer templateCustomizer) throws RenderingException {
        return null;
    }
}
