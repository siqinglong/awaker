package com.future.awaker.news.viewmodel;

import android.databinding.Bindable;
import android.databinding.ObservableArrayList;
import android.databinding.ObservableField;
import android.databinding.ObservableList;

import com.future.awaker.BR;
import com.future.awaker.R;
import com.future.awaker.base.viewmodel.BaseListViewModel;
import com.future.awaker.data.Comment;
import com.future.awaker.data.Header;
import com.future.awaker.data.NewDetail;
import com.future.awaker.data.realm.CommentHotRealm;
import com.future.awaker.data.realm.CommentRealm;
import com.future.awaker.data.realm.NewDetailRealm;
import com.future.awaker.network.EmptyConsumer;
import com.future.awaker.network.ErrorConsumer;
import com.future.awaker.news.listener.SendCommentListener;
import com.future.awaker.source.AwakerRepository;
import com.future.awaker.util.LogUtils;
import com.future.awaker.util.ResUtils;

import java.util.HashMap;
import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.realm.RealmList;
import io.realm.RealmResults;

/**
 * Created by ruzhan on 2017/7/6.
 */

public class NewDetailViewModel extends BaseListViewModel {

    private static final String TAG = NewDetailViewModel.class.getSimpleName();

    public ObservableField<NewDetail> newDetail = new ObservableField<>();
    public ObservableList<Comment> comments = new ObservableArrayList<>();
    public Header header = new Header();

    private String newId;
    private HashMap<String, String> map = new HashMap<>();
    private HashMap<String, String> commentMap = new HashMap<>();
    private String commentCount = "0";

    private SendCommentListener listener;


    public NewDetailViewModel(SendCommentListener listener) {
        this.listener = listener;
    }

    @Bindable
    public String getCommentCount() {
        return ResUtils.getString(R.string.new_comment_count, commentCount);
    }

    public void setCommentCount(String commentCount) {
        this.commentCount = commentCount;
        notifyPropertyChanged(BR.commentCount);
    }

    public String getNewId() {
        return newId;
    }

    public void setNewId(String newId) {
        this.newId = newId;
        map.put(NewDetailRealm.ID, newId + NewDetailRealm.DETAIL);
        commentMap.put(CommentHotRealm.ID, newId + CommentHotRealm.COMMENT_HOT);
    }

    public String getTitle() {
        return header.title;
    }

    public void setTitle(String title) {
        this.header.title = title;
    }

    public String getUrl() {
        return header.url;
    }

    public void setUrl(String url) {
        this.header.url = url;
    }

    @Override
    public void refreshData(boolean refresh) {
        if (refresh && newDetail.get() == null) {
            getLocalNewDetail();
        }
        getRemoteNewDetail();


    }

    private void getLocalNewDetail() {
        disposable.add(AwakerRepository.get().getLocalRealm(NewDetailRealm.class, map)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError(throwable -> LogUtils.showLog(TAG, "doOnError: " + throwable.toString()))
                .doOnSubscribe(disposable -> isRunning.set(true))
                .doOnTerminate(() -> isRunning.set(false))
                .doOnNext(realmResults -> {
                    LogUtils.d("getLocalNewDetail" + realmResults.size());
                    setLocalNewDetail(realmResults);
                })
                .subscribe(new EmptyConsumer(), new ErrorConsumer()));
    }

    private void setLocalNewDetail(RealmResults realmResults) {
        if (realmResults == null || realmResults.isEmpty()) {
            return;
        }
        NewDetailRealm newDetailRealm = (NewDetailRealm) realmResults.get(0);
        if (newDetail.get() == null) {   // data is empty, network not back
            NewDetail newDetailItem = NewDetailRealm.setNewDetailRealm(newDetailRealm);
            setDataObject(newDetailItem, newDetail);
        }
    }

    private void getRemoteNewDetail() {
        disposable.add(AwakerRepository.get().getNewDetail(TOKEN, newId)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError(throwable -> isError.set(throwable))
                .doOnSubscribe(disposable -> isRunning.set(true))
                .doOnTerminate(() -> isRunning.set(false))
                .doOnNext(httpResult -> setRemoteNewDetail(httpResult.getData()))
                .subscribe(new EmptyConsumer(), new ErrorConsumer()));
    }

    private void setRemoteNewDetail(NewDetail newDetailItem) {
        setDataObject(newDetailItem, newDetail);

        if (!isEmpty.get()) {
            // save to local
            NewDetailRealm newDetailRealm = NewDetailRealm.setNewDetail(newDetailItem);
            AwakerRepository.get().updateLocalRealm(newDetailRealm);
        }
    }

    public void getHotCommentList() {
        if (comments.isEmpty()) {
            getLocalHotCommentList();
        }
        getRemoteHotCommentList();
    }

    public void getRemoteHotCommentList() {
        disposable.add(AwakerRepository.get().getUpNewsComments(TOKEN, newId)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError(throwable -> LogUtils.showLog(TAG, "remote doOnError: " + throwable.toString()))
                .doOnNext(result -> setRemoteHotCommentList(result.getData()))
                .subscribe(new EmptyConsumer(), new ErrorConsumer()));
    }

    private void setRemoteHotCommentList(List<Comment> commentList) {
        if (commentList != null && !commentList.isEmpty()) {
            comments.clear();
            comments.addAll(commentList);

            CommentHotRealm commentHotRealm = new CommentHotRealm();
            commentHotRealm.setComment_hot_id(newId + CommentHotRealm.COMMENT_HOT);
            RealmList<CommentRealm> realms = CommentHotRealm.getRealmList(commentList);
            commentHotRealm.setCommentList(realms);
            AwakerRepository.get().updateLocalRealm(commentHotRealm);
        }
    }

    public void getLocalHotCommentList() {
        disposable.add(AwakerRepository.get().getLocalRealm(CommentHotRealm.class, commentMap)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError(throwable -> LogUtils.showLog(TAG, "doOnError: " + throwable.toString()))
                .doOnNext(realmResults -> {
                    LogUtils.d("getLocalHotCommentList" + realmResults.size());
                    setLocalHotCommentList(realmResults);
                })
                .subscribe(new EmptyConsumer(), new ErrorConsumer()));
    }

    private void setLocalHotCommentList(RealmResults realmResults) {
        if (realmResults == null || realmResults.isEmpty()) {
            return;
        }
        CommentHotRealm commentHotRealm = (CommentHotRealm) realmResults.get(0);
        if (comments.isEmpty()) {   // data is empty, network not back
            RealmList<CommentRealm> commentRealms = commentHotRealm.getCommentList();
            List<Comment> commentList = CommentHotRealm.getList(commentRealms);
            comments.clear();
            comments.addAll(commentList);
        }
    }

    public void sendNewsComment(String newId, String content, String open_id,
                                String pid) {
        disposable.add(AwakerRepository.get().sendNewsComment(TOKEN, newId, content, open_id, pid)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError(throwable -> LogUtils.showLog(TAG, "remote doOnError: " + throwable.toString()))
                .doOnNext(result -> listener.sendCommentSuc())
                .subscribe(new EmptyConsumer(), new ErrorConsumer()));
    }
}
