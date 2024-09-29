package eu.bosteels.mercator.mono;

import org.apache.commons.text.StringEscapeUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

public class AsListTest {

    @Test
    public void asList() {
        Repository.asList(List.of("abc", "a{} b[] c()"));
        Repository.asList(List.of("abc", "cdf"));
        Repository.asList(List.of("abc"));
        Repository.asList(List.of());
    }

    @Test
    public void escape() {
        String input = "abc\"xyz";
        String output = StringEscapeUtils.escapeCsv(input);
        System.out.println("input  = " + input);
        System.out.println("output = " + output);
    }

}
