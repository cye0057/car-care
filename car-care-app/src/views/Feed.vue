<template>
  <div>
    <van-nav-bar title="养车笔记">
      <template #right><van-icon name="edit" size="18" @click="$router.push('/publish')" /></template>
    </van-nav-bar>
    <van-pull-refresh v-model="refreshing" @refresh="reload">
      <div class="page">
        <van-empty v-if="!list.length && !loading" description="关注车主后其笔记会出现在这里，或去发布一篇" />
        <div v-for="b in list" :key="b.id" class="blog">
          <div class="head">
            <!-- Vant 4.10 无 avatar 组件，自定义圆形头像：有图取图，无图显示昵称首字 -->
            <div class="ava" :style="b.userAvatar ? { backgroundImage: `url(${b.userAvatar})` } : {}">
              {{ b.userAvatar ? '' : (b.userName || '?')[0] }}
            </div>
            <div class="who">
              <b>{{ b.userName }}</b>
              <span class="meta">{{ fmt(b.createTime) }} · {{ b.storeName }}</span>
            </div>
            <van-tag v-if="b.score" type="warning" plain>{{ '★'.repeat(b.score) }}</van-tag>
          </div>
          <div class="content">{{ b.content }}</div>
          <div class="acts">
            <span :class="{ liked: b.liked }" @click="toggleLike(b)">
              <van-icon :name="b.liked ? 'like' : 'like-o'" /> {{ b.likedCount }}
            </span>
          </div>
        </div>
        <van-loading v-if="loading" class="more" size="20">加载更多...</van-loading>
        <div v-else-if="list.length" class="more">— 到底啦 —</div>
      </div>
    </van-pull-refresh>
  </div>
</template>

<script setup>
import { nextTick, onActivated, ref } from 'vue'
import { feedApi, likeApi, unlikeApi } from '../api'

const list = ref([])
const cursor = ref({})
const hasMore = ref(true)
const loading = ref(false)
const refreshing = ref(false)

const fmt = (t) => (t ? String(t).replace('T', ' ').slice(5, 16) : '')

async function loadPage(reset = false) {
  if (loading.value) return
  if (!reset && !hasMore.value) return
  loading.value = true
  try {
    const params = reset ? {} : { maxId: cursor.value.nextMaxId, beginTime: cursor.value.nextBeginTime }
    const res = await feedApi(params)
    const fresh = res.data.list
    list.value = reset ? fresh : [...list.value, ...fresh]
    cursor.value = { nextMaxId: res.data.nextMaxId, nextBeginTime: res.data.nextBeginTime }
    hasMore.value = res.data.hasMore
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function reload() { refreshing.value = true; loadPage(true) }

async function toggleLike(b) {
  const res = b.liked ? await unlikeApi(b.id) : await likeApi(b.id)
  if (res.data) {
    b.liked = !b.liked
    b.likedCount += b.liked ? 1 : -1
  }
}

onActivated(() => nextTick(() => { if (!list.value.length) loadPage(true) }))
loadPage(true)
</script>

<style scoped>
.blog { background: #fff; border-radius: 10px; padding: 12px; margin-bottom: 10px; }
.head { display: flex; align-items: center; gap: 8px; }
.ava { width: 34px; height: 34px; border-radius: 50%; flex-shrink: 0; overflow: hidden;
       display: flex; align-items: center; justify-content: center;
       background: linear-gradient(135deg, #0d9488, #14b8a6) center / cover no-repeat;
       color: #fff; font-size: 14px; font-weight: 600; }
.who { flex: 1; }
.who b { font-size: 14px; }
.meta { display: block; color: #aaa; font-size: 11px; }
.content { margin: 8px 0; font-size: 14px; line-height: 1.6; }
.acts { color: #999; font-size: 13px; }
.acts .liked { color: #ee0a24; }
.more { text-align: center; color: #bbb; font-size: 12px; padding: 8px; }
</style>
