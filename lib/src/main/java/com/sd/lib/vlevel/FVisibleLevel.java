package com.sd.lib.vlevel;

import android.util.Log;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public abstract class FVisibleLevel
{
    private static final Map<Class<? extends FVisibleLevel>, FVisibleLevel> MAP_LEVEL = new ConcurrentHashMap<>();

    private final Map<Class<? extends FVisibleLevelItem>, FVisibleLevelItem> mMapLevelItem = new ConcurrentHashMap<>();
    private final Map<VisibilityCallback, String> mVisibilityCallbackHolder = new WeakHashMap<>();

    private boolean mIsVisible = true;
    private FVisibleLevelItem mVisibleItem;

    private static boolean sIsDebug;

    protected FVisibleLevel()
    {
    }

    public static void setDebug(boolean isDebug)
    {
        sIsDebug = isDebug;
    }

    /**
     * 返回某个等级
     *
     * @param clazz
     * @return
     */
    public static synchronized FVisibleLevel get(Class<? extends FVisibleLevel> clazz)
    {
        if (clazz == null)
            throw new NullPointerException("clazz is null");

        if (clazz == FVisibleLevel.class)
            throw new IllegalArgumentException("clazz is " + clazz.getName());

        FVisibleLevel level = MAP_LEVEL.get(clazz);
        if (level == null)
        {
            level = createLevel(clazz);
            if (level == null)
                throw new RuntimeException("create level failed " + clazz.getName());

            final Class<? extends FVisibleLevelItem>[] classes = level.onCreate();
            if (classes == null || classes.length <= 0)
                throw new RuntimeException("level onCreate() return null or empty " + clazz.getName());

            for (Class<? extends FVisibleLevelItem> itemClass : classes)
            {
                checkLevelItemClass(itemClass);
                level.mMapLevelItem.put(itemClass, InternalItem.DEFAULT);
            }

            MAP_LEVEL.put(clazz, level);

            if (sIsDebug)
            {
                final StringBuilder builder = new StringBuilder("+++++ ");
                builder.append(clazz.getName()).append(" create").append("\r\n");
                for (Class<? extends FVisibleLevelItem> itemClass : classes)
                {
                    builder.append("item:").append(itemClass.getName()).append("\r\n");
                }
                Log.i(FVisibleLevel.class.getSimpleName(), builder.toString());
            }
        }
        return level;
    }

    /**
     * 清空所有等级
     */
    public static synchronized void clearLevel()
    {
        MAP_LEVEL.clear();

        if (sIsDebug)
            Log.i(FVisibleLevel.class.getSimpleName(), "clearLevel");
    }

    /**
     * 创建回调
     *
     * @return
     */
    protected abstract Class<? extends FVisibleLevelItem>[] onCreate();

    /**
     * 返回某个Item
     *
     * @param clazz
     * @return
     */
    public final FVisibleLevelItem getItem(Class<? extends FVisibleLevelItem> clazz)
    {
        return getOrCreateItem(clazz);
    }

    private FVisibleLevelItem getOrCreateItem(Class<? extends FVisibleLevelItem> clazz)
    {
        checkLevelItemClass(clazz);

        FVisibleLevelItem item = mMapLevelItem.get(clazz);
        if (item == null)
            throw new RuntimeException("Item " + clazz.getName() + " was not found in level " + FVisibleLevel.this);

        if (item == InternalItem.DEFAULT)
        {
            item = createLevelItem(clazz);
            if (item == null)
                throw new RuntimeException("create level item failed " + clazz.getName());

            mMapLevelItem.put(clazz, item);

            if (sIsDebug)
                Log.i(FVisibleLevel.class.getSimpleName(), getClass().getName() + " create levelItem:" + clazz.getName());

            item.onCreate();
        }
        return item;
    }

    /**
     * 添加回调，弱引用保存回调对象
     *
     * @param callback
     */
    public final void addVisibilityCallback(VisibilityCallback callback)
    {
        if (callback != null)
            mVisibilityCallbackHolder.put(callback, "");
    }

    /**
     * 移除回调
     *
     * @param callback
     */
    public final void removeVisibilityCallback(VisibilityCallback callback)
    {
        if (callback != null)
            mVisibilityCallbackHolder.remove(callback);
    }

    private Collection<VisibilityCallback> getVisibilityCallbacks()
    {
        return Collections.unmodifiableCollection(mVisibilityCallbackHolder.keySet());
    }

    /**
     * 等级是否可见
     *
     * @return
     */
    public final boolean isVisible()
    {
        return mIsVisible;
    }

    /**
     * 返回可见的Item
     *
     * @return
     */
    public final FVisibleLevelItem getVisibleItem()
    {
        return mVisibleItem;
    }

    /**
     * 设置等级是否可见
     *
     * @param visible
     */
    public final void setVisible(boolean visible)
    {
        if (mIsVisible != visible)
        {
            mIsVisible = visible;
            if (sIsDebug)
                Log.i(FVisibleLevel.class.getSimpleName(), getClass().getName() + " setVisible:" + visible);

            for (VisibilityCallback callback : getVisibilityCallbacks())
            {
                callback.onLevelVisibilityChanged(visible, FVisibleLevel.this);
            }
            visibleItemInternal(visible, mVisibleItem);
        }
    }

    /**
     * 设置Item可见
     *
     * @param clazz
     */
    public final void visibleItem(Class<? extends FVisibleLevelItem> clazz)
    {
        if (!mIsVisible)
            throw new RuntimeException("level is not visible:" + getClass().getName());

        if (sIsDebug)
            Log.i(FVisibleLevel.class.getSimpleName(), getClass().getName() + " visibleItem:" + clazz.getName());

        final FVisibleLevelItem item = getOrCreateItem(clazz);
        final FVisibleLevelItem old = mVisibleItem;
        if (old != item)
        {
            if (old != null)
                visibleItemInternal(false, old);

            mVisibleItem = item;
            visibleItemInternal(true, item);
        }
    }

    /**
     * 设置当前可见的Item为不可见
     */
    public final void invisibleCurrentItem()
    {
        if (sIsDebug)
            Log.i(FVisibleLevel.class.getSimpleName(), getClass().getName() + " invisibleCurrentItem");

        visibleItemInternal(false, mVisibleItem);
        mVisibleItem = null;
    }

    /**
     * 通知可见Item
     */
    public final void notifyCurrentVisibleItem()
    {
        if (sIsDebug)
            Log.i(FVisibleLevel.class.getSimpleName(), getClass().getName() + " notifyCurrentVisibleItem");

        visibleItemInternal(true, mVisibleItem);
    }

    private void visibleItemInternal(boolean visible, FVisibleLevelItem item)
    {
        if (item == null)
            return;

        if (sIsDebug)
            Log.i(FVisibleLevel.class.getSimpleName(), getClass().getName() + " visibleItemInternal visible:" + visible + " item:" + item.getClass().getName());

        if (mMapLevelItem.containsKey(item.getClass()))
        {
            item.notifyVisibility(visible);
        } else
        {
            throw new RuntimeException("Item " + item.getClass().getName() + " was not found in level " + FVisibleLevel.this);
        }
    }

    private static FVisibleLevel createLevel(Class<? extends FVisibleLevel> clazz)
    {
        try
        {
            return clazz.newInstance();
        } catch (IllegalAccessException e)
        {
            e.printStackTrace();
        } catch (InstantiationException e)
        {
            e.printStackTrace();
        }
        return null;
    }

    private static FVisibleLevelItem createLevelItem(Class<? extends FVisibleLevelItem> clazz)
    {
        try
        {
            return clazz.newInstance();
        } catch (IllegalAccessException e)
        {
            e.printStackTrace();
        } catch (InstantiationException e)
        {
            e.printStackTrace();
        }
        return null;
    }

    private static void checkLevelItemClass(Class<? extends FVisibleLevelItem> clazz)
    {
        if (clazz == null)
            throw new NullPointerException("clazz is null");

        if (clazz == FVisibleLevelItem.class)
            throw new IllegalArgumentException("clazz is " + clazz.getName());

        if (!FVisibleLevelItem.class.isAssignableFrom(clazz))
            throw new IllegalArgumentException(FVisibleLevelItem.class.getName() + " is not assignable from " + clazz.getName());
    }

    static final class InternalItem extends FVisibleLevelItem
    {
        public static final InternalItem DEFAULT = new InternalItem();

        @Override
        public void onCreate()
        {
        }
    }

    public interface VisibilityCallback
    {
        /**
         * 等级可见状态变化回调
         *
         * @param visible
         * @param level
         */
        void onLevelVisibilityChanged(boolean visible, FVisibleLevel level);
    }
}
