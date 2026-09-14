package io.github.lynxus.example.nativeimage;

import io.github.lynxus.api.ResultColumn;
import io.github.lynxus.api.SqlResult;

import java.util.List;

public final class NativeImageSmoke {

    private NativeImageSmoke() {
    }

    public static void main(String[] args) {
        NativeImageMapper mapper = new NativeImageMapperImpl(plan ->
            SqlResult.forQuery(
                List.of(new ResultColumn("id", 0), new ResultColumn("name", 1)),
                List.<Object[]>of(new Object[]{plan.getParameters()[0], "generated"})));

        NativeImageUser user = mapper.findById(7L);
        if (!new NativeImageUser(7L, "generated").equals(user)) {
            throw new IllegalStateException("Unexpected generated Mapper result: " + user);
        }
        System.out.println("Lynxus Native Image example passed");
    }
}
