package com.alibot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Bootstrap bootstrap = new Bootstrap();
    private final Media media = new Media();
    private final Order order = new Order();
    private final Conversation conversation = new Conversation();
    private final MiniApp miniApp = new MiniApp();
    private final Schedule schedule = new Schedule();
    private final Crm crm = new Crm();

    private String internalApiKey;

    public Bootstrap getBootstrap() {
        return bootstrap;
    }

    public Media getMedia() {
        return media;
    }

    public Order getOrder() {
        return order;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public MiniApp getMiniApp() {
        return miniApp;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public Crm getCrm() {
        return crm;
    }

    public String getInternalApiKey() {
        return internalApiKey;
    }

    public void setInternalApiKey(String internalApiKey) {
        this.internalApiKey = internalApiKey;
    }

    public static class Bootstrap {
        private String superadminTelegramId;

        public String getSuperadminTelegramId() {
            return superadminTelegramId;
        }

        public void setSuperadminTelegramId(String superadminTelegramId) {
            this.superadminTelegramId = superadminTelegramId;
        }
    }

    public static class Media {
        private String storageDir = "./data/media";
        private int maxVideoSizeMb = 20;
        private int maxPhotoSizeMb = 20;
        private int maxMediaCountPerVisit = 20;
        /** ТЗ п.100. 0 — хранить бессрочно (по умолчанию: удаление файлов необратимо, включать
         *  явным решением администратора, а не тихим дефолтом — тот же принцип, что и у
         *  app.crm.webhook-url). */
        private int retentionDays = 0;
        private String retentionCron = "0 0 3 * * *";

        public String getStorageDir() {
            return storageDir;
        }

        public void setStorageDir(String storageDir) {
            this.storageDir = storageDir;
        }

        public int getMaxVideoSizeMb() {
            return maxVideoSizeMb;
        }

        public void setMaxVideoSizeMb(int maxVideoSizeMb) {
            this.maxVideoSizeMb = maxVideoSizeMb;
        }

        public int getMaxPhotoSizeMb() {
            return maxPhotoSizeMb;
        }

        public void setMaxPhotoSizeMb(int maxPhotoSizeMb) {
            this.maxPhotoSizeMb = maxPhotoSizeMb;
        }

        public int getMaxMediaCountPerVisit() {
            return maxMediaCountPerVisit;
        }

        public void setMaxMediaCountPerVisit(int maxMediaCountPerVisit) {
            this.maxMediaCountPerVisit = maxMediaCountPerVisit;
        }

        public int getRetentionDays() {
            return retentionDays;
        }

        public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
        }

        public String getRetentionCron() {
            return retentionCron;
        }

        public void setRetentionCron(String retentionCron) {
            this.retentionCron = retentionCron;
        }
    }

    public static class Order {
        private int acceptTimeoutMinutes = 10;
        private int reminderMinutes = 60;
        private int maxContactAttempts = 3;

        public int getAcceptTimeoutMinutes() {
            return acceptTimeoutMinutes;
        }

        public void setAcceptTimeoutMinutes(int acceptTimeoutMinutes) {
            this.acceptTimeoutMinutes = acceptTimeoutMinutes;
        }

        public int getReminderMinutes() {
            return reminderMinutes;
        }

        public void setReminderMinutes(int reminderMinutes) {
            this.reminderMinutes = reminderMinutes;
        }

        public int getMaxContactAttempts() {
            return maxContactAttempts;
        }

        public void setMaxContactAttempts(int maxContactAttempts) {
            this.maxContactAttempts = maxContactAttempts;
        }
    }

    public static class Conversation {
        private int timeoutMinutes = 30;

        public int getTimeoutMinutes() {
            return timeoutMinutes;
        }

        public void setTimeoutMinutes(int timeoutMinutes) {
            this.timeoutMinutes = timeoutMinutes;
        }
    }

    public static class MiniApp {
        private String baseUrl;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class Schedule {
        /** ТЗ п.79 — "время настраивается"; по умолчанию 20:00 каждый день. */
        private String dailyReportCron = "0 0 20 * * *";
        /** Как часто проверять напоминания/эскалации (ТЗ п.83/84). */
        private String timersCron = "0 */5 * * * *";

        public String getDailyReportCron() {
            return dailyReportCron;
        }

        public void setDailyReportCron(String dailyReportCron) {
            this.dailyReportCron = dailyReportCron;
        }

        public String getTimersCron() {
            return timersCron;
        }

        public void setTimersCron(String timersCron) {
            this.timersCron = timersCron;
        }
    }

    /** ТЗ п.85-87 — CRMAdapter: вебхук во внешнюю CRM + ретраи. Пусто по умолчанию — CRM не настроена. */
    public static class Crm {
        private String webhookUrl;
        private String webhookSecret;
        private int maxAttempts = 5;
        private String retryCron = "0 * * * * *";

        public String getWebhookUrl() {
            return webhookUrl;
        }

        public void setWebhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }

        public String getWebhookSecret() {
            return webhookSecret;
        }

        public void setWebhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public String getRetryCron() {
            return retryCron;
        }

        public void setRetryCron(String retryCron) {
            this.retryCron = retryCron;
        }
    }
}
