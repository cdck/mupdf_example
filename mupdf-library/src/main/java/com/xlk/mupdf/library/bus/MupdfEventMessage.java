package com.xlk.mupdf.library.bus;

/**
 * @author Created by xlk on 2021/9/17.
 * @desc
 */
public class MupdfEventMessage {
    private String type;
    private int method;
    private Object[] objects;

    private MupdfEventMessage(Builder builder) {
        this.type = builder.type;
        this.method = builder.method;
        this.objects = builder.objects;
    }

    public String getType() {
        return type;
    }

    public int getMethod() {
        return method;
    }

    public Object[] getObjects() {
        return objects;
    }

    public static class Builder {
        private String type;
        private int method;
        private Object[] objects;

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder method(int method) {
            this.method = method;
            return this;
        }

        public Builder objects(Object... objects) {
            this.objects = objects;
            return this;
        }

        public MupdfEventMessage build() {
            return new MupdfEventMessage(this);
        }
    }
}
