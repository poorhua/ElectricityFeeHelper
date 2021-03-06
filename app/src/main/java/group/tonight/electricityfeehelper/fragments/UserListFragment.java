package group.tonight.electricityfeehelper.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.scwang.smartrefresh.layout.api.RefreshLayout;
import com.scwang.smartrefresh.layout.listener.OnRefreshLoadMoreListener;
import com.socks.library.KLog;

import org.greenrobot.greendao.query.QueryBuilder;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import group.tonight.electricityfeehelper.MainApp;
import group.tonight.electricityfeehelper.R;
import group.tonight.electricityfeehelper.activities.UserInfoActivity;
import group.tonight.electricityfeehelper.dao.DaoSession;
import group.tonight.electricityfeehelper.dao.User;
import group.tonight.electricityfeehelper.dao.UserDao;
import group.tonight.electricityfeehelper.interfaces.OnFragmentInteractionListener;
import group.tonight.electricityfeehelper.utils.MyUtils;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 用户库
 */
public class UserListFragment extends Fragment implements OnFragmentInteractionListener {
    private static final String ARG_COLUMN_COUNT = "column-count";
    private int mColumnCount = 1;
    private TextView mCountView;
    private RecyclerView mListView;
    private RefreshLayout mRefreshLayout;

    public UserListFragment() {
    }

    @SuppressWarnings("unused")
    public static UserListFragment newInstance(int columnCount) {
        UserListFragment fragment = new UserListFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_COLUMN_COUNT, columnCount);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mColumnCount = getArguments().getInt(ARG_COLUMN_COUNT);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_user_list, container, false);
        mRefreshLayout = (RefreshLayout) view.findViewById(R.id.smart_refresh_layout);
        mRefreshLayout.setOnRefreshLoadMoreListener(new OnRefreshLoadMoreListener() {
            @Override
            public void onLoadMore(RefreshLayout refreshLayout) {
                refreshLayout.finishLoadMore();
            }

            @Override
            public void onRefresh(RefreshLayout refreshLayout) {
                refreshLayout.finishRefresh();
                OkGo.<List<User>>get(MyUtils.LATEST_USER_URL)
                        .execute(new AbsCallback<List<User>>() {
                            @Override
                            public void onSuccess(com.lzy.okgo.model.Response<List<User>> response) {
                                List<User> userList = response.body();
                                if (userList != null) {
                                    KLog.e(userList.size());
                                    mCountView.setText(userList.size() + "");
                                    mBaseQuickAdapter.replaceData(userList);
                                }
                            }

                            @Override
                            public List<User> convertResponse(Response response) throws Throwable {
                                ResponseBody responseBody = response.body();
                                if (responseBody == null) {
                                    return null;
                                }
                                String json = responseBody.string();
                                JSONArray jsonArray = new JSONArray(json);
                                FragmentActivity activity = getActivity();
                                if (activity == null) {
                                    return null;
                                }
                                for (int i = 0; i < jsonArray.length(); i++) {
                                    String userInfoUrl = jsonArray.getString(i);
                                    Response execute = OkGo.<byte[]>get(userInfoUrl)
                                            .execute();
                                    ResponseBody responseBody1 = execute.body();
                                    if (responseBody1 == null) {
                                        return null;
                                    }
                                    MyUtils.saveUserListToDb(responseBody1.bytes());
                                }
                                return MainApp.getDaoSession().getUserDao().loadAll();
                            }
                        });
            }
        });
        mListView = (RecyclerView) view.findViewById(R.id.list);
        mCountView = (TextView) view.findViewById(R.id.count);

        setHasOptionsMenu(true);

        // Set the adapter
        final Context context = mListView.getContext();

        mListView.addItemDecoration(new DividerItemDecoration(context, DividerItemDecoration.VERTICAL));

        if (mColumnCount <= 1) {
            mListView.setLayoutManager(new LinearLayoutManager(context));
        } else {
            mListView.setLayoutManager(new GridLayoutManager(context, mColumnCount));
        }
        mListView.setAdapter(mBaseQuickAdapter);
        mBaseQuickAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                Intent intent = new Intent(view.getContext(), UserInfoActivity.class);
                intent.putExtra("_id", ((User) adapter.getItem(position)).getId());
                startActivity(intent);
            }
        });
        new Thread(new Runnable() {
            @Override
            public void run() {
                UserDao userDao = MainApp.getDaoSession().getUserDao();
                final List<User> list = userDao.loadAll();
                KLog.e("onCreateView: " + list.size());
                if (getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mCountView.setText(list.size() + "");
                            mBaseQuickAdapter.replaceData(list);
                        }
                    });
                }
            }
        }).start();
        OkGo.<List<User>>get("http://192.168.1.121:8080/getallpoweruserinfo")
                .execute(new AbsCallback<List<User>>() {
                    @Override
                    public void onSuccess(com.lzy.okgo.model.Response<List<User>> response) {
                        KLog.e();
                    }

                    @Override
                    public List<User> convertResponse(Response response) throws Throwable {
                        JSONObject jsonObject = new JSONObject(response.body().string());
                        if (jsonObject.getInt("code") == 0) {
                            Type type = new TypeToken<List<User>>() {
                            }.getType();
                            return new Gson().fromJson(jsonObject.getJSONArray("data").toString(), type);
                        }
                        return new ArrayList<>();
                    }
                });
        return view;
    }

    /**
     * 重写这个方法要先调用setHasOptionsMenu方法
     *
     * @param menu
     * @param inflater
     */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.clear();
        inflater.inflate(R.menu.menu_user_list_fragment, menu);

        SearchView mSearchView = (SearchView) menu.findItem(R.id.menu_search).getActionView();
        mSearchView.setQueryHint("电能表编号、姓名、手机、用户编号");
//        mSearchView.setIconifiedByDefault(false);//false表示加载后默认为搜索框输入状态
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                KLog.e(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                KLog.e(newText);
                if (getActivity() != null) {
                    DaoSession daoSession = MainApp.getDaoSession();
                    UserDao userDao = daoSession.getUserDao();

                    QueryBuilder<User> userQueryBuilder = userDao.queryBuilder()
                            .whereOr(
                                    UserDao.Properties.UserId.like("%" + newText + "%")//匹配用户ID
                                    , UserDao.Properties.UserName.like("%" + newText + "%")//匹配用户姓名
                                    , UserDao.Properties.PowerMeterId.like("%" + newText + "%")//匹配电能表编号
                                    , UserDao.Properties.UserPhone.like("%" + newText + "%")//匹配用户手机
                            );

                    List<User> list = userQueryBuilder
                            .limit(50)
                            .list();
                    mBaseQuickAdapter.replaceData(list);
                }
                return false;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add_user:
                AddUserFragment.newInstance(0).show(getChildFragmentManager(), "");
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onFragmentInteraction(int result) {
        KLog.e("onFragmentInteraction: " + result);
        FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }
        List<User> userList = MainApp.getDaoSession().getUserDao().loadAll();
        mCountView.setText(userList.size() + "");
        mBaseQuickAdapter.replaceData(userList);
    }

    private BaseQuickAdapter<User, BaseViewHolder> mBaseQuickAdapter = new BaseQuickAdapter<User, BaseViewHolder>(R.layout.fragment_user) {
        @Override
        protected void convert(BaseViewHolder helper, User item) {
            helper.setText(R.id.power_meter_id, item.getPowerMeterId());
            helper.setText(R.id.id, item.getUserId());
            helper.setText(R.id.content, item.getUserName());
            helper.setText(R.id.phone, item.getUserPhone());
        }
    };
}
