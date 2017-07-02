package zsy;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by MissingNo on 2017/5/17.
 */
class Tuple {
    private String subject;
    private List<String> description= new ArrayList<>();

    // 一堆getter，setter方法
    Tuple(String subject, List<String> description) {
        this.subject = "";
        this.subject = subject;
        this.description = description;
    }

    String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    List<String> getDescription() {
        return description;
    }

    void setDescription(List<String> description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "Tuple [subject=" + subject + ", description=" + description
                + "]";
    }

}