<template>
  <div>
    <van-nav-bar :title="orderId ? '评价订单' : '发布笔记'" left-arrow @click-left="$router.back()" />
    <div class="page">
      <van-cell-group inset>
        <van-cell title="关联门店" :value="storeText" :is-link="!orderId" @click="onStoreCell" />
        <van-cell v-if="orderNo" title="订单号" :value="orderNo" />
        <van-cell title="评分">
          <van-rate v-model="form.score" />
        </van-cell>
      </van-cell-group>
      <van-field v-model="form.content" type="textarea" rows="4" maxlength="500" show-count
                 :placeholder="orderId ? '这次服务体验怎么样？' : '分享你的保养体验，帮助更多车主~'"
                 style="margin-top: 10px; border-radius: 8px" />
      <div style="margin: 12px 12px 0">
        <van-uploader v-model="imageList" :max-count="6" :after-read="afterRead" />
      </div>
      <div style="margin: 16px 12px">
        <van-button block round color="#0d9488" :loading="submitting" @click="submit">
          {{ orderId ? '提交评价' : '发布' }}
        </van-button>
      </div>
      <van-popup v-model:show="showStore" position="bottom" round>
        <van-picker :columns="storeColumns" @confirm="onStore" @cancel="showStore = false" />
      </van-popup>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { publishApi, storesApi, uploadApi } from '../api'

const route = useRoute()
const router = useRouter()
const stores = ref([])
const store = ref(null)
const showStore = ref(false)
const submitting = ref(false)
const form = reactive({ score: 5, content: '' })
const imageList = ref([])

/** 从订单页进入时锁定门店与订单，避免评到别家店 */
const orderId = computed(() => (route.query.orderId ? Number(route.query.orderId) : null))
const orderNo = computed(() => route.query.orderNo || '')

/** 选中图片后逐张上传 OSS，成功用返回的 URL 替换本地预览，失败移除该张 */
async function afterRead(item) {
  const fd = new FormData()
  fd.append('file', item.file)
  item.status = 'uploading'
  item.message = '上传中'
  try {
    const res = await uploadApi(fd)
    item.url = res.data
    item.status = 'done'
    item.message = ''
  } catch (e) {
    imageList.value = imageList.value.filter((i) => i !== item)
  }
}

const storeText = computed(() => store.value?.name || '选择门店')
const storeColumns = computed(() => stores.value.map((s) => ({ text: s.name, value: s })))

onMounted(async () => {
  if (orderId.value) {
    // 订单评价：门店由订单带过来，只显示不可改
    store.value = { id: Number(route.query.storeId), name: route.query.storeName || '门店' }
    return
  }
  stores.value = (await storesApi()).data
  // 从门店详情页进入时预选该店，仍可点开换店
  if (route.query.storeId) {
    const pre = stores.value.find((s) => String(s.id) === String(route.query.storeId))
    if (pre) store.value = pre
  }
})

function onStoreCell() {
  if (!orderId.value) showStore.value = true
}

/* Vant 的 selectedValues 装的已是选项的 value 字段本身，不能再取一层 .value */
function onStore({ selectedValues }) { store.value = selectedValues[0]; showStore.value = false }

async function submit() {
  if (!store.value) return showToast('请选择门店')
  if (!form.content.trim()) return showToast('说点什么吧')
  submitting.value = true
  try {
    const images = imageList.value.filter((i) => i.url).map((i) => i.url).join(',')
    await publishApi({
      storeId: store.value.id,
      orderId: orderId.value,
      score: form.score,
      content: form.content,
      images
    })
    showToast(orderId.value ? '评价成功' : '发布成功，已推送给关注你的粉丝')
    router.push(orderId.value ? '/orders' : '/feed')
  } finally {
    submitting.value = false
  }
}
</script>
