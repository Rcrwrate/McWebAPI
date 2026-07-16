package love.shirokasoke.webapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public final class Constant {

    private Constant() {}

    public static ObjectMapper mapper = new ObjectMapper().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        .disable(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS)
        .enable(SerializationFeature.WRITE_SELF_REFERENCES_AS_NULL)
        .disable(SerializationFeature.INDENT_OUTPUT);
}
