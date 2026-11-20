private static Object extractBufferSource(Minecraft mc, Object levelRenderer) {
        String[] bufferMethodNames = {
                "bufferSource", "getBufferSource",
                "outlineBufferSource", "getOutlineBufferSource",
                "multiBufferSource", "getMultiBufferSource",
                "buffers", "getBuffers",
                "consumers", "getConsumers"
        };
        String[] bufferFieldNames = {
                "bufferSource", "outlineBufferSource", "multiBufferSource", "buffers", "consumers"
        };

        // 1. Пробуем достать напрямую из levelRenderer (если передан)
        Object fromLevel = invokeOrReadAny(levelRenderer, bufferMethodNames, bufferFieldNames);
        if (fromLevel != null) {
            Object nested = invokeOrReadAny(fromLevel, bufferMethodNames, bufferFieldNames);
            return nested != null ? nested : fromLevel;
        }

        // 2. Пробуем достать через renderBuffers / getRenderBuffers у Minecraft
        if (mc != null) {
            Object renderBuffers = invokeOrReadAny(
                    mc,
                    new String[]{"renderBuffers", "getRenderBuffers"},
                    new String[]{"renderBuffers"}
            );
            if (renderBuffers != null) {
                Object bs = invokeOrReadAny(renderBuffers, bufferMethodNames, bufferFieldNames);
                if (bs != null) return bs;
            }

            // 3. Пробуем напрямую у Minecraft
            Object directMc = invokeOrReadAny(mc, bufferMethodNames, bufferFieldNames);
            if (directMc != null) return directMc;
        }

        return null;
    }

    private static Object invokeOrReadAny(Object target, String[] methodNames, String[] fieldNames) {
        if (target == null) return null;
        Class<?> cls = target.getClass();

        for (String mName : methodNames) {
            try {
                Method m = cls.getMethod(mName);
                if (m.getParameterCount() == 0) {
                    Object val = m.invoke(target);
                    if (val != null) return val;
                }
            } catch (Throwable ignored) {
            }
            try {
                Method m = cls.getDeclaredMethod(mName);
                if (m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    Object val = m.invoke(target);
                    if (val != null) return val;
                }
            } catch (Throwable ignored) {
            }
        }

        for (String fName : fieldNames) {
            try {
                Field f = cls.getField(fName);
                Object val = f.get(target);
                if (val != null) return val;
            } catch (Throwable ignored) {
            }
            try {
                Field f = cls.getDeclaredField(fName);
                f.setAccessible(true);
                Object val = f.get(target);
                if (val != null) return val;
            } catch (Throwable ignored) {
            }
        }

        return null;
    }
