package com.atguigu.gulimall.product.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.constant.ProductConstant;
import com.atguigu.common.to.SkuHasStockVo;
import com.atguigu.common.to.SkuReductionTo;
import com.atguigu.common.to.SpuBoundTo;
import com.atguigu.common.to.es.SkuEsModel;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.product.entity.*;
import com.atguigu.gulimall.product.feign.CouponFeignService;
import com.atguigu.gulimall.product.feign.SearchFeignService;
import com.atguigu.gulimall.product.feign.WareFeignService;
import com.atguigu.gulimall.product.service.*;
import com.atguigu.gulimall.product.vo.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.product.dao.SpuInfoDao;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


@Service("spuInfoService")
public class SpuInfoServiceImpl extends ServiceImpl<SpuInfoDao, SpuInfoEntity> implements SpuInfoService {

    @Autowired
    SkuInfoService skuInfoService;

    @Autowired
    SpuInfoDescService spuInfoDescService;

    @Autowired
    SpuImagesService imagesService;

    @Autowired
    AttrService attrService;

    @Autowired
    ProductAttrValueService attrValueService;

    @Autowired
    SkuImagesService skuImagesService;

    @Autowired
    SkuSaleAttrValueService skuSaleAttrValueService;

    @Autowired
    CouponFeignService couponFeignService;

    @Autowired
    BrandService brandService;

    @Autowired
    CategoryService categoryService;

    @Autowired
    WareFeignService wareFeignService;

    @Autowired
    SearchFeignService searchFeignService;


    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                new QueryWrapper<SpuInfoEntity>()
        );

        return new PageUtils(page);
    }

    // 19???????????????: ????????????????????????????????????
    @Transactional
    @Override
    public void saveSpuInfo(SpuSaveVo vo) {
        //1?????????spu???????????? pms_spu_info
        SpuInfoEntity infoEntity = new SpuInfoEntity();
        BeanUtils.copyProperties(vo, infoEntity);
        infoEntity.setCreateTime(new Date());
        infoEntity.setUpdateTime(new Date());
        //??????????????????
        this.saveBaseSpuInfo(infoEntity);

        //2?????????Spu??????????????? pms_spu_info_desc
        List<String> decript = vo.getDecript();
        SpuInfoDescEntity descEntity = new SpuInfoDescEntity();
        descEntity.setSpuId(infoEntity.getId()); //??????????????????
        descEntity.setDecript(String.join(",", decript));
        spuInfoDescService.saveSpuInfoDesc(descEntity);

        //3?????????spu???????????? pms_spu_images
        List<String> images = vo.getImages();
        imagesService.saveImages(infoEntity.getId(), images); //?????????????????????

        //4?????????spu???????????????;pms_product_attr_value
        //vo -> ProductAttrValueEntity
        List<BaseAttrs> baseAttrs = vo.getBaseAttrs();
        List<ProductAttrValueEntity> valueEntities = baseAttrs.stream().map(attr -> {
            ProductAttrValueEntity valueEntity = new ProductAttrValueEntity();
            //attr_id
            valueEntity.setAttrId(attr.getAttrId());
            //attr_id -> attrEntity -> attr_name
            AttrEntity attrEntity = attrService.getById(attr.getAttrId());
            valueEntity.setAttrName(attrEntity.getAttrName());
            //values
            valueEntity.setAttrValue(attr.getAttrValues());
            //desc
            valueEntity.setQuickShow(attr.getShowDesc());
            //spuId
            valueEntity.setSpuId(infoEntity.getId());

            return valueEntity;
        }).collect(Collectors.toList());
        //??????????????????
        attrValueService.saveProductAttr(valueEntities);

        //5?????????spu??????????????????gulimall_sms->sms_spu_bounds
        Bounds bounds = vo.getBounds();
        SpuBoundTo spuBoundTo = new SpuBoundTo();
        BeanUtils.copyProperties(bounds, spuBoundTo);
        spuBoundTo.setSpuId(infoEntity.getId());
        //coupon ????????????
        R r = couponFeignService.saveSpuBounds(spuBoundTo);
        if(r.getCode() != 0){
            log.error("????????????spu??????????????????");
        }


        //6???????????????spu???????????????sku?????????
        //SKU: ???????????????????????????8+128G / 12+256G
        List<Skus> skus = vo.getSkus();
        //6.1 cornercase
        if (skus != null && skus.size() > 0) {
            //6.2 ????????????sku(????????????)
            skus.forEach(item -> {
                //6.3 ??????????????????
                String defaultImg = "";
                for (Images image : item.getImages()) {
                    if(image.getDefaultImg() == 1){
                        defaultImg = image.getImgUrl();
                    }
                }
                //6.4 sku??????????????????pms_sku_info
                SkuInfoEntity skuInfoEntity = new SkuInfoEntity();
                BeanUtils.copyProperties(item, skuInfoEntity);
                //spu - BrandId
                skuInfoEntity.setBrandId(infoEntity.getBrandId());
                //spu - catelogId
                skuInfoEntity.setCatalogId(infoEntity.getCatalogId());
                //count
                skuInfoEntity.setSaleCount(0L);
                //spu - id
                skuInfoEntity.setSpuId(infoEntity.getId());
                //default image
                skuInfoEntity.setSkuDefaultImg(defaultImg);
                //??????????????????
                skuInfoService.saveSkuInfo(skuInfoEntity);

                //6.5 sku??????????????????pms_sku_image
                Long skuId = skuInfoEntity.getSkuId();

                List<SkuImagesEntity> imagesEntities = item.getImages().stream().map(img -> {
                    SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                    //sku - id
                    skuImagesEntity.setSkuId(skuId);
                    //image url
                    skuImagesEntity.setImgUrl(img.getImgUrl());
                    //default image
                    skuImagesEntity.setDefaultImg(img.getDefaultImg());
                    return skuImagesEntity;
                }).filter(entity->{
                    //??????true???????????????false????????????
                    return !StringUtils.isEmpty(entity.getImgUrl());
                }).collect(Collectors.toList());
                //??????????????????
                skuImagesService.saveBatch(imagesEntities);

                //6.6 sku????????????????????????pms_sku_sale_attr_value
                List<Attr> attr = item.getAttr();
                List<SkuSaleAttrValueEntity> skuSaleAttrValueEntities = attr.stream().map(a -> {
                    SkuSaleAttrValueEntity attrValueEntity = new SkuSaleAttrValueEntity();
                    BeanUtils.copyProperties(a, attrValueEntity);
                    attrValueEntity.setSkuId(skuId);

                    return attrValueEntity;
                }).collect(Collectors.toList());
                //??????????????????
                skuSaleAttrValueService.saveBatch(skuSaleAttrValueEntities);

                //6.7 sku??????????????????????????????gulimall_sms->sms_sku_ladder\sms_sku_full_reduction\sms_member_price
                SkuReductionTo skuReductionTo = new SkuReductionTo();
                BeanUtils.copyProperties(item, skuReductionTo);
                skuReductionTo.setSkuId(skuId);
                if (skuReductionTo.getFullCount() > 0 || skuReductionTo.getFullPrice().
                        compareTo(new BigDecimal("0")) == 1) {
                    //coupon ????????????
                    R r1 = couponFeignService.saveSkuReduction(skuReductionTo);
                    if (r1.getCode() != 0) {
                        log.error("????????????sku??????????????????");
                    }
                }
            });
        }
    }

    //??????????????????
    @Override
    public void saveBaseSpuInfo(SpuInfoEntity infoEntity) {
        baseMapper.insert(infoEntity);
    }

    //18???spu?????? - pms_spu_info
    @Override
    public PageUtils queryPageByCondition(Map<String, Object> params) {
        //??????
        /*
        key: '??????',//???????????????
        catelogId: 6,//????????????id
        brandId: 1,//??????id
        status: 0,//????????????
        */

        QueryWrapper<SpuInfoEntity> wrapper = new QueryWrapper<>();
        //1.key = id + name
        String key = (String) params.get("key");
        if(!StringUtils.isEmpty(key)){
            wrapper.and((w) -> {
                w.eq("id", key).or().eq("spu_name", key);
            });
        }

        //2.status
        String status = (String) params.get("status");
        if (!StringUtils.isEmpty(status)) {
            wrapper.eq("publish_status", status);
        }

        //3.brandId
        String brandId = (String) params.get("brandId");
        if (!StringUtils.isEmpty(brandId) && !"0".equalsIgnoreCase(brandId)) {
            wrapper.eq("brand_id", brandId);
        }

        //4.catelogId
        String catelogId = (String) params.get("catelogId");
        if (!StringUtils.isEmpty(catelogId) && !"0".equalsIgnoreCase(catelogId)) {
            wrapper.eq("catalog_id", catelogId);
        }

        //??????
        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                wrapper
        );
        return new PageUtils(page);
    }

    //20??????????????? #dadada
    //SkuInfoEntity -> SkuEsModel
    @Override
    public void up(Long spuId) {
        //1.?????? spuid -> sku????????????????????????
        List<SkuInfoEntity> skus = skuInfoService.getSkusBySpuId(spuId);
        List<Long> skuIdList = skus.stream().map(SkuInfoEntity::getSkuId).collect(Collectors.toList());

        //2.????????????sku??????????????????????????????????????????????????????
        //pms_product_attr_value
        //spuId -> productAttrValueEntity -> attr_id -> attrEntity -> search_type
        //2.1 spuId -> productAttrValueEntity
        List<ProductAttrValueEntity> attrValueEntities = attrValueService.baseAttrlistforspu(spuId);

        //2.2 productAttrValueEntity -> attr_id
        List<Long> attrIds = attrValueEntities.stream().map(attr -> {
            return attr.getAttrId();
        }).collect(Collectors.toList());

        //2.3 attrEntity -> search_type
        List<Long> searchAttrIds = attrService.selectSearchAttrIds(attrIds);

        Set<Long> searchSet = new HashSet<>(searchAttrIds);

        //2.4 attrValueEntities ??? searchType = 0 ?????????, ????????????
        //Set????????? searchType=0 ?????????id???????????????
        List<SkuEsModel.Attrs> attrsList = attrValueEntities.stream().filter(item -> {
            return searchSet.contains(item.getAttrId());
        }).map(item -> {
            //2.5  attrValueEntities -> SkuEsModel
            SkuEsModel.Attrs attrs = new SkuEsModel.Attrs();
            BeanUtils.copyProperties(item, attrs);
            return attrs;
        }).collect(Collectors.toList());

        //3 ???????????????ware ?????? ???????????????
        Map<Long, Boolean> stockMap = null;
        try {
            R skuHasStock = wareFeignService.getSkuHasStock(skuIdList);
            TypeReference<List<SkuHasStockVo>> typeReference = new TypeReference<List<SkuHasStockVo>>(){};
            //SkuHasStockVo -> Map<getSkuId, getHasStock>
            stockMap = skuHasStock.getData(typeReference).stream().collect(Collectors.toMap(SkuHasStockVo::getSkuId, SkuHasStockVo::getHasStock));
        } catch (Exception e) {
            log.error("Stock error: {}", e);
        }


        //4.?????? sku ??????
        Map<Long, Boolean> finalStockMap = stockMap;
        List<SkuEsModel> upProducts = skus.stream().map(sku -> {
            //1.????????????????????? SkuInfoEntity -> SkuEsModel
            SkuEsModel esModel = new SkuEsModel();
            BeanUtils.copyProperties(sku, esModel);
            //2.skuPrice, skuImg
            esModel.setSkuPrice(sku.getPrice());
            esModel.setSkuImg(sku.getSkuDefaultImg());
            //3.hasStock, hotScore
            //3.1 ???????????????
            if (finalStockMap == null) {
                esModel.setHasStock(false);
            }
            else{
                esModel.setHasStock(finalStockMap.get(sku.getSkuId()));
            }

            //3.2 ????????????
            esModel.setHotScore(0L);

            //4.??????????????????????????????
            BrandEntity brand = brandService.getById(esModel.getBrandId());
            esModel.setBrandName(brand.getName());
            esModel.setBrandImg(brand.getLogo());

            //5.????????????????????????
            CategoryEntity category = categoryService.getById(esModel.getCatalogId());
            esModel.setCatalogName(category.getName());

            //6.??????????????????attr
            esModel.setAttrs(attrsList);

            return esModel;
        }).collect(Collectors.toList());

        //5.???????????????es?????? gulimall-search ????????????
        R r = searchFeignService.productStatusUp(upProducts);
        if(r.getCode() == 0){
            //3.1 ??????????????????,????????????spu?????? (??????spu_id ?????? status ??? update-time)
            // baseMapper.updateSpuStatus(spuId, ProductConstant.ProductStatusEnum.SPU_UP.getCode());

            QueryWrapper<SpuInfoEntity> wrapper = new QueryWrapper<>();
            wrapper.eq("id", spuId);

            SpuInfoEntity spuInfoEntity = new SpuInfoEntity();
            // spuInfoEntity.setId(spuId);
            spuInfoEntity.setPublishStatus(1);
            spuInfoEntity.setUpdateTime(new Date());

            baseMapper.update(spuInfoEntity, wrapper);
        }
        // else {
        //     //TODO ????????????
        // }

    }

    @Override
    public SpuInfoEntity getSpuInfoBySkuId(Long skuId) {
        return getById(skuInfoService.getById(skuId).getSpuId());
    }
}

























