package com.github.skyjack2033.wirelessmehatch.loader;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class CustomWirelessToolRemovalTest {

    private static final Path ROOT = Paths.get(System.getProperty("user.dir"));

    @Test
    public void customToolSourcesAndTextureAreDeleted() {
        assertMissing("src/main/java/com/github/skyjack2033/wirelessmehatch/item/ItemWirelessLinkTool.java");
        assertMissing("src/main/java/com/github/skyjack2033/wirelessmehatch/loader/ItemLoader.java");
        assertMissing("src/test/java/com/github/skyjack2033/wirelessmehatch/item/ItemWirelessLinkToolTest.java");
        assertMissing("src/main/resources/assets/wirelessmehatch/textures/items/wireless_link_tool.png");
    }

    @Test
    public void registrationRecipeAndLanguagesContainNoCustomToolReference() throws IOException {
        String commonProxy = read("src/main/java/com/github/skyjack2033/wirelessmehatch/CommonProxy.java");
        String recipeLoader = read("src/main/java/com/github/skyjack2033/wirelessmehatch/loader/RecipeLoader.java");
        String wirelessTarget = read("src/main/java/com/github/skyjack2033/wirelessmehatch/me/WirelessLinkTarget.java");
        String english = read("src/main/resources/assets/wirelessmehatch/lang/en_US.lang");
        String chinese = read("src/main/resources/assets/wirelessmehatch/lang/zh_CN.lang");

        assertFalse(commonProxy.contains("ItemLoader"));
        assertFalse(recipeLoader.contains("ItemLoader"));
        assertFalse(recipeLoader.contains("registerLinkToolRecipe"));
        assertFalse(recipeLoader.contains("WIRELESS_LINK_TOOL"));
        assertFalse(english.contains("wirelessmehatch.link_tool"));
        assertFalse(chinese.contains("wirelessmehatch.link_tool"));
        assertFalse(wirelessTarget.contains("forTile("));
        assertTrue(recipeLoader.contains("toolWirelessKit()"));
    }

    private static void assertMissing(String path) {
        assertFalse(path + " must be deleted", Files.exists(ROOT.resolve(path)));
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(ROOT.resolve(path)), StandardCharsets.UTF_8);
    }
}
