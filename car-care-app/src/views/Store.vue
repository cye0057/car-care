<template>
  <div>
    <van-nav-bar title="门店详情" left-arrow @click-left="$router.back()" />
    <div class="page" v-if="store">
      <van-cell-group inset>
        <van-cell :title="store.name" :label="store.address">
          <template #value><span class="price">⭐ {{ store.score }}</span></template>
        </van-cell>
        <van-cell title="营业时间" :value="store.businessHours" />
        <van-cell title="电话" :value="store.phone" />
      </van-cell-group>

      <div class="card-title">保养项目</div>
      <van-cell v-for="it in items" :key="it.id" :title="it.name" :label="it.description" is-link
                :value="`¥${it.price}`" @click="goCreate({ itemId: it.id })" />

      <div class="card-title">超值套餐</div>
      <van-cell v-for="p in packages" :key="p.pkg.id" :title="p.pkg.name" :label="p.pkg.description" is-link
                :value="`¥${p.pkg.price}`" @click="goCreate({ packageId: p.pkg.id })" />

      <div class="card-title">
        车主评价
        <span class="write-btn" @click="writeReview">写评价</span>
      </div>
      <div v-for="r in reviews" :key="r.id" class="review">
        <div class="review-head">
          <div class="ava" :style="r.userAvatar ? { backgroundImage: `url(${r.userAvatar})` } : {}">
            {{ r.userAvatar ? '' : (r.userName || '?')[0] }}
          </div>
          <div>
            <b>{{ r.userName }}</b> <span class="stars">{{ '★'.repeat(r.score) }}</span>
          </div>
        </div>
        <div class="rc">{{ r.content }}</div>
      </div>
      <van-empty v-if="!reviews.length" description="暂无评价" image-size="50" />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeDetailApi, storeItemsApi, storePackagesApi, storeReviewsApi } from '../api'

const route = useRoute()
const router = useRouter()
const store = ref(null)
const items = ref([])
const packages = ref([])
const reviews = ref([])

const id = route.params.id
Promise.all([storeDetailApi(id), storeItemsApi(id), storePackagesApi(id), storeReviewsApi(id)])
  .then(([a, b, c, d]) => {
    store.value = a.data
    items.value = b.data
    packages.value = c.data
    reviews.value = d.data.records
  })

function goCreate(q) {
  router.push({ path: '/order-create', query: { storeId: id, ...q } })
}

/** 门店页写评价：只是普通晒图笔记，不绑定订单（要评价具体订单请从订单页进入） */
function writeReview() {
  router.push({ path: '/publish', query: { storeId: id, storeName: store.value?.name || '' } })
}
</script>

<style scoped>
.stars { color: #ff9900; font-size: 12px; margin-left: 6px; }
.write-btn { float: right; color: #0d9488; font-size: 13px; font-weight: 400; }
.review { background: #fff; border-radius: 8px; padding: 10px 12px; margin: 6px 4px; font-size: 13px; }
.review-head { display: flex; align-items: center; gap: 8px; }
.ava {
  width: 28px; height: 28px; border-radius: 50%; overflow: hidden; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #0d9488, #14b8a6) center / cover no-repeat;
  color: #fff; font-size: 12px; font-weight: 600;
}
.rc { color: #666; margin-top: 4px; }
</style>
