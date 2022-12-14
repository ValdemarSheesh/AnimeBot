package com.example.animebot.service;

import com.example.animebot.model.Animechan;
import com.example.animebot.client.JSONLoader;
import com.example.animebot.config.BotConfig;
import com.example.animebot.model.User;
import com.example.animebot.model.UserRepository;
import com.google.gson.Gson;
import com.vdurmont.emoji.EmojiParser;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Log4j2
@Component
public class TelegramBot extends TelegramWebhookBot {

    @Autowired
    private UserRepository userRepository;
    final BotConfig config;
    final String URL_ANIMECHAN = "https://animechan.vercel.app/api/random";
    final String URL_WAIFU = "https://api.waifu.pics/sfw/%s";
    final String URL_KYOKO = "https://kyoko.rei.my.id/api/%s.php";
    final String URL_SET_WEBHOOK = "https://api.telegram.org/bot%s/setWebhook?url=%s";
    final List<String> categoryWaifu = List.of("waifu", "megumin", "dance", "kick", "cry", "blush", "kiss", "cuddle", "hug", "pat", "bonk", "smile", "nom", "happy");
    final Map<String, String> categoryKyoko = Map.of("??????????", "sfw", "????????????????", "blush", "????????", "bonk", "??????????????", "hug", "????????????????", "slap", "????????????", "smile", "????????????????????????", "wink", "????????????????", "wave");

    public TelegramBot(BotConfig config) {
        this.config = config;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "????????????????!"));
        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Error setting bot`s command list: " + e.getMessage());
        }


        try {
            JSONLoader.getJSON(String.format(URL_SET_WEBHOOK, config.getToken(), config.getWebHookPath()));
        } catch (IOException | InterruptedException e) {
            log.error("Error set webHook: " + e.getMessage());
        }

//        try {
//            setWebhook(SetWebhook.builder().url().build());
//        } catch (TelegramApiException e) {
//            throw new RuntimeException(e);
//        }
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    @Override
    public String getBotPath() {
        return config.getWebHookPath();
    }

    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        if (update.getMessage() != null && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();

            switch (messageText) {
                case "/start" -> {
                    registerUser(update.getMessage());
                    startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                }
                case "/back" -> sendStartKeyBoard(chatId);
                case "???????????????? ???????????? ???? ??????????" -> sendQuote(chatId);
                case "???????????????? ??????????/?????? 1 ???????????? (16+)" -> sendCategoryWaifu(chatId);
                case "???????????????? ??????????/?????? 2 ???????????? (16+)" -> sendCategoryKyoko(chatId);
                default -> {
                    if (categoryWaifu.contains(messageText))
                        sendImageWaifu(chatId, messageText);
                    if (categoryKyoko.containsKey(messageText))
                        sendImageKyoko(chatId, categoryKyoko.get(messageText));
                }
            }
        }
        return null;
    }

    private void sendStartKeyBoard(long chatId) {
        String useButtons = "???????????????????????? ???????????????? ????????)";
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(useButtons);
        message.setReplyMarkup(getKeyboardStart());
        executeMessage(message);
    }

    private void sendImageWaifu(long chatId, String messageText) {
        String jsonStr;
        try {
            jsonStr = JSONLoader.getJSON(String.format(URL_WAIFU, messageText));
            JSONObject jsonObject = new JSONObject(jsonStr);
            InputFile file = new InputFile();
            String url = jsonObject.getString("url");
            sendImage(chatId, file, url);
        } catch (Exception e) {
            sendMessServiceUnavailable(chatId);
            log.error(String.format("Error url Waifu (%s): %s", URL_WAIFU, e.getMessage()));
        }
    }

    private void sendImageKyoko(long chatId, String messageText) {
        String jsonStr;
        try {
            jsonStr = JSONLoader.getJSON(String.format(URL_KYOKO, messageText));
            JSONObject jsonObject = new JSONObject(jsonStr);
            InputFile file = new InputFile();
            String url = jsonObject.getJSONObject("apiResult").getJSONArray("url").get(0).toString();
            sendImage(chatId, file, url);
        } catch (Exception e) {
            sendMessServiceUnavailable(chatId);
            log.error(String.format("Error url Kyoko (%s): %s", URL_KYOKO, e.getMessage()));
        }
    }

    private void sendImage(long chatId, InputFile file, String url) {
        file.setMedia(url);

        if (url.matches("\\S+.gif")) {
            SendAnimation sendAnimation = new SendAnimation();
            sendAnimation.setChatId(chatId);
            sendAnimation.setAnimation(file);
            executeMessage(sendAnimation);
        } else {
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId);
            sendPhoto.setPhoto(file);
            executeMessage(sendPhoto);
        }
    }

    private void sendCategoryWaifu(long chatId) {
        SendMessage message = new SendMessage(String.valueOf(chatId), "???????????? ??????????????????");
        message.setReplyMarkup(getKeyboardCategoryWaifu());
        executeMessage(message);
    }

    private void sendCategoryKyoko(long chatId) {
        SendMessage message = new SendMessage(String.valueOf(chatId), "???????????? ??????????????????");
        message.setReplyMarkup(getKeyboardCategoryKyoko());
        executeMessage(message);
    }

    private void registerUser(Message message) {
        if (userRepository.findById(message.getChatId()).isEmpty()) {
            var chatId = message.getChatId();
            var chat = message.getChat();

            User user = new User();

            user.setChatId(chatId);
            user.setFirsName(chat.getFirstName());
            user.setLastName(chat.getLastName());
            user.setUserName(chat.getUserName());

            userRepository.save(user);
            log.info("user: " + user);
        }
    }

    private ReplyKeyboard getKeyboardCategoryWaifu() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();

        for (String category : categoryWaifu) {
            if (row.size() > 4) {
                keyboardRows.add(row);
                row = new KeyboardRow();
            }
            row.add(category);
        }
        row.add(EmojiParser.parseToUnicode("/back"));
        keyboardRows.add(row);

        keyboardMarkup.setKeyboard(keyboardRows);
        return keyboardMarkup;
    }

    private ReplyKeyboard getKeyboardCategoryKyoko() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();

        for (String category : categoryKyoko.keySet()) {
            if (row.size() > 2) {
                keyboardRows.add(row);
                row = new KeyboardRow();
            }
            row.add(category);
        }
        row.add(EmojiParser.parseToUnicode("/back"));
        keyboardRows.add(row);

        keyboardMarkup.setKeyboard(keyboardRows);
        return keyboardMarkup;
    }

    private void sendQuote(long chatId) {
        Gson gson = new Gson();
        Animechan animechan;
        try {
            animechan = gson.fromJson(JSONLoader.getJSON(URL_ANIMECHAN), Animechan.class);
            executeMessage(new SendMessage(String.valueOf(chatId), animechan.toString()));
        } catch (Exception e) {
            sendMessServiceUnavailable(chatId);
            log.error(String.format("Error url Animechan (%s): %s", URL_ANIMECHAN, e.getMessage()));
        }
    }

    private ReplyKeyboardMarkup getKeyboardStart() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();

        row.add("???????????????? ???????????? ???? ??????????");
        keyboardRows.add(row);

        row = new KeyboardRow();
        row.add("???????????????? ??????????/?????? 1 ???????????? (16+)");
        keyboardRows.add(row);

        row = new KeyboardRow();
        row.add("???????????????? ??????????/?????? 2 ???????????? (16+)");
        keyboardRows.add(row);

        keyboardMarkup.setKeyboard(keyboardRows);
        return keyboardMarkup;
    }

    private void startCommandReceived(long chatId, String name) {
        String greeting = EmojiParser.parseToUnicode("??????????????????????, " + name + " :smile:" +
                "\n???????????????????????? ???????????????? ????????)");
        SendMessage message = new SendMessage(String.valueOf(chatId), greeting);
        message.setReplyMarkup(getKeyboardStart());
        executeMessage(message);
    }

    private void executeMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error occurred send message: " + e.getMessage());
        }
    }

    private void executeMessage(SendPhoto photo) {
        try {
            execute(photo);
        } catch (TelegramApiException e) {
            log.error("Error occurred send photo: " + e.getMessage());
        }
    }

    private void executeMessage(SendAnimation animation) {
        try {
            execute(animation);
        } catch (TelegramApiException e) {
            log.error("Error occurred send animation: " + e.getMessage());
        }
    }

    private void sendMessServiceUnavailable(long chatId) {
        String warning = EmojiParser.parseToUnicode("???????????? ???????????????? ???????????????????? :cry:");
        SendMessage message = new SendMessage(String.valueOf(chatId), warning);
        executeMessage(message);
    }
}










