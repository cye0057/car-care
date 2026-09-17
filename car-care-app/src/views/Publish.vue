<template>
  <div>
    <van-nav-bar title="发布笔记" left-arrow @click-left="$router.back()" />
    <div class="page">
      <van-cell-group inset>
        <van-cell title="关联门店" :value="storeText" is-link @click="showStore = true" />
        <van-cell title="评分">
          <van-rate v-model="form.score" />
        </van-cell>
      </van-cell-group>
      <van-field v-model="form.content" type="textarea" rows="4" maxlength="500" show-count
                 placeholder="分享你的保养体验，帮助更多车主~" style="margin-top: 10px; border-radius: 8px" />
      <div style="margin: 12px 12px 0">
        <van-uploader v-model="imageList" :max-count="6" :after-read="afterRead" />
      </div>
      <div style="margin: 16px 12px">
        <van-button block round color="#0d9488" :loading="submitting" @click="submit">发布</van-button>
      </div>
      <van-popup v-model:show="showStore" position="bottom" round>
        <van-picker :columns="storeColumns" @confirm="onStore" @cancel="showStore = false" />
      </van-popup>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { publishApi, storesApi, uploadApi } from '../api'

const router = useRouter()
const stores = ref([])
const store = ref(null)
const showStore = ref(false)
const submitting = ref(false)
const form = reactive({ score: 5, content: '' })
const imageList = ref([])

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

onMounted(async () => { stores.value = (await storesApi()).data })
function onStore({ selectedValues }) { store.value = selectedValues[0].value; showStore.value = false }

async function submit() {
  if (!store.value) return showToast('请选择门店')
  if (!form.content.trim()) return showToast('说点什么吧')
  submitting.value = true
  try {
    const images = imageList.value.filter((i) => i.url).map((i) => i.url).join(',')
    await publishApi({ storeId: store.value.id, score: form.score, content: form.content, images })
    showToast('发布成功，已推送给关注你的粉丝')
    router.push('/feed')
  } finally {
    submitting.value = false
  }
}
</script>
