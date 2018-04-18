import sys
import time
import datetime
import re
import io
import socks
import random
import configparser

from telethon import utils, events, TelegramClient, ConnectionMode
from telethon.tl.functions.channels import GetFullChannelRequest
from telethon.tl.types import InputChannel, PeerChat
from telethon.tl.functions.contacts import ResolveUsernameRequest
from telethon.tl.functions.messages import CheckChatInviteRequest
from telethon.extensions import markdown

class ChannelBlock(object):
    def __init__(self):
        self.name = ''
        self.title = ''
        self.id = 0
        self.entity = None

class RegBlock(object):
    def __init__(self):
        self.name = ''
        self.title = ''
        self.rating = 0
        self.reg_has = None
        self.reg_skip = None
        self.reg_coin = None
        self.reg_coin2 = None
        self.reg_price_from = None
        self.reg_price_to = None
        self.reg_price_target = None
        self.reg_price_stoploss = None

class SignalRow(object):
    def __init__(self):
        self.title = ''
        self.coin = None
        self.coin2 = None
        self.price_from = None
        self.price_to = None
        self.price_target = None
        self.price_stoploss = None
        self.rating = None
        self.date = None
        self.index = None
        self.sort = 0.0

    def init_sort(self):
        self.sort = 0.0
        if self.price_from:
            self.sort = self.sort + 1.0
        if self.price_to:
            self.sort = self.sort + 1.0
        if self.price_target:
            self.sort = self.sort + 1.0
        if self.coin2:
            self.sort = self.sort + 0.75
        self.sort = self.sort + self.rating / 20.0

    def __repr__(self):
        return str(self.rating) + ";" + self.date + ";" + self.coin + ";" + self.coin2 + ";" + self.price_from + ";" + self.price_to + ";" + self.price_target + ';' + self.price_stoploss + ';' + str(self.index) + ";" + str(self.title)

def enable_win_unicode_console():
    if sys.version_info >= (3, 6):
        return
    import win_unicode_console
    win_unicode_console.enable() 

def getFirstMatch(regex, txt):
    if regex:
        try:
            matches = re.finditer(regex, txt, re.UNICODE | re.IGNORECASE | re.MULTILINE)
            for matchNum, match in enumerate(matches):
                for groupNum in range(0, len(match.groups())):
                    return match.group(1)
        except:
            matches = []
    return ''

def coinFix(txt):
    try:
        if txt:
            txt = txt.replace('*', '').strip(',.+ \n\t').upper()
    except:
        txt = ''
    return txt

def dateFix(date):
    try:
        ts = time.time()
        utc_offset = (datetime.datetime.fromtimestamp(ts) - datetime.datetime.utcfromtimestamp(ts)).total_seconds()
        datetime_obj_naive = datetime.datetime.strptime(str(date), "%Y-%m-%d %H:%M:%S")
        datetime_obj_naive = datetime_obj_naive + datetime.timedelta(seconds = utc_offset)
    except:
        datetime_obj_naive = ''
    return str(datetime_obj_naive)

def percentFix(txt, price):
    if txt and txt.count('%') > 0:
        if price:
            txt = txt.replace('%', '')
            txt = numFix(txt)
            if txt:
                numtxt = float(txt)
                nump = float(price)
                numtxt = nump * 0.01 * (100-numtxt)
                txt = str(numtxt).replace(',', '.')
        else:
            txt = ''
    else:
        txt = numFix(txt)
    return txt

def numFix(txt):
    try:
        if txt:
            txt = txt.replace(',', '.').strip(',.+ \n\t')
            if txt.count('.') > 1:
                rtxt = getFirstMatch(r'^(0\.[0-9]{2,12})', txt)
                if not rtxt:
                    rtxt = getFirstMatch(r'^([0-9]{2,12})', txt)
                if not rtxt:
                    rtxt = float(getFirstMatch(r'^([0-9.]{2,12})', txt))
                    rtxt = str(rtxt).replace(',', '.')
                txt = rtxt
        if txt and (txt.find('k')>=0 or txt.find('K')>=0):
            num = float(getFirstMatch(r'^([0-9.]{2,12})', txt))
            num = num * 1000
            txt = str(num).replace(',', '.')
    except:
        txt = ''
    return txt

def separateCheck(txt, title, date, client):
    if (txt is None) or (txt == ''):
        return

    global log
    if log:
        log.write("------------------------------\n")
        log.write(txt)
        log.write("\n------------------------------\n")

    non_bmp_map = dict.fromkeys(range(0x10000, sys.maxunicode + 1), 0xfffd)
    txt = txt.translate(non_bmp_map)
    title = title.translate(non_bmp_map).replace(';', ' ')
    global signal_separator
    if signal_separator:
        parts = re.split(signal_separator, txt, flags = re.UNICODE | re.IGNORECASE | re.MULTILINE)
        if parts and len(parts) > 0:
            i = 0
            while i < len(parts):
                textCheck(parts[i], title, date, client)
                i = i + 1
        else:
            textCheck(txt, title, date, client)
    else:
        textCheck(txt, title, date, client)
    return

def textCheck(txt, title, date, client):
    global signal_expr
    global log

    stop_coin2_names = [
        'BINANCE', 
        'BITTREX', 
        'YOBIT', 
        'CRYPTOPIA', 
        'EXCHANGE', 
        'SIGNAL', 
        'PALM', 
        'BUY', 
        'SELL', 
        'CRYPTOL', 
        'LEOSIGN', 
        '2GIVE', 
        'DIGIXDA', 
        'NEWS', 
        'PREMIUM', 
        'ZONE', 
        'UPDATE', 
        'PRICE', 
        'COIN', 
        'VIP', 
        'INSIDE', 
        'PRIVATE', 
        'AGAIN', 
        'RESULT', 
        'RESULTS', 
        'BELOW', 
        'BETWEEN', 
        'AROUND', 
        'NEAR', 
        'AREA', 
        'STOP', 
        'EARLY', 
        'MIDTERM', 
        'SHORTTERM', 
        'SHORT', 
        'LONGTERM', 
        'LONGTER', 
        'CRYPTOR', 
        'TRUSTPL', 
        'CRYPTOW', 
        'PART', 
        'OK', 
        'SOME'
    ]
    stop_coin1_names = list(stop_coin2_names)
    stop_coin1_names = stop_coin1_names + [
        'BTC',
        'USD',
        'USDT'
    ]
    best_item = None

    if log:
        log.write("    Matching rools: ")

    i = 0
    while i < len(signal_expr):
        if signal_expr[i].reg_has:
            hasmatch = getFirstMatch(signal_expr[i].reg_has, txt)
            skipmatch = getFirstMatch(signal_expr[i].reg_skip, txt)
            if hasmatch and not skipmatch:
                coin = coinFix(getFirstMatch(signal_expr[i].reg_coin, txt))
                coin2 = coinFix(getFirstMatch(signal_expr[i].reg_coin2, txt))
                price_from = numFix(getFirstMatch(signal_expr[i].reg_price_from, txt))
                price_to = numFix(getFirstMatch(signal_expr[i].reg_price_to, txt))
                price_target = numFix(getFirstMatch(signal_expr[i].reg_price_target, txt))
                price_stoploss = percentFix(getFirstMatch(signal_expr[i].reg_price_stoploss, txt), price_from)
                if log:
                    log.write(" " + str(i + 1))
                    if coin:
                        if coin in stop_coin1_names:
                            log.write("[-coin]")
                        else:
                            log.write("[coin]")
                    if coin2:
                        if coin2 in stop_coin2_names:
                            log.write("[-coin2]")
                        else:
                            log.write("[coin2]")
                    if price_from:
                        log.write("[price1]")
                    if price_to:
                        log.write("[price2]")
                    if price_target:
                        log.write("[target]")
                    if price_stoploss:
                        log.write("[stop]")
                if coin and (coin not in stop_coin1_names) and (coin2 not in stop_coin2_names) and (price_from or price_to or price_target):
                    ri = SignalRow()
                    ri.title = title
                    ri.coin = coin
                    ri.coin2 = coin2
                    ri.price_from = price_from
                    ri.price_to = price_to
                    ri.price_target = price_target
                    ri.price_stoploss = price_stoploss
                    ri.rating = signal_expr[i].rating
                    ri.date = date
                    ri.index = i + 1
                    ri.init_sort()
                    if best_item is None or best_item.sort < ri.sort:
                        best_item = ri
        i = i + 1

    if best_item is not None:
        print(str(best_item))
        if log:
            log.write("\n    <--- "+str(best_item)+"\n\n")
    else:
        if log:
            log.write("\n    <--- NOT A SIGNAL MESSAGE\n\n")

    return

def getChannelTitleForEvent(channels, event):
    try:
        title = ''
        channel_id = event.message.to_id.channel_id
        if channel_id > 0:
            i = 0
            while i < len(channels):
                if channels[i].entity and channels[i].entity.channel_id and channels[i].entity.channel_id == channel_id:
                    title = channels[i].title
                    if not title:
                        title = channels[i].name
                    if not title:
                        title = str(channels[i].id)
                i = i + 1
    except:
        title = ''
    if not title:
        title = '-'
    return title

def checkCurchan(channel, client, signals_limit):
    chantitle = channel.title
    if not chantitle:
        chantitle = channel.name
    if not chantitle:
        chantitle = str(channel.id)

    global log
    if log:
        log.write("\n\n")
        log.write("------------------------------\n")
        log.write("BEGIN CHECKING CHANNEL: "+channel.title+" - " + channel.name +" - " + str(channel.id) + "\n")
        log.write("------------------------------\n")
        log.write("\n\n")

    channame = channel.name

    if not channame and channel.id != 0:
        int_chan = int(channel.id)
    else:
        int_chan = 0

    if int_chan != 0:
        try:
            input_channel =  client(GetFullChannelRequest(int_chan))
            channel_id = input_channel.full_chat.id
        except Exception as e:
            print(str(e))
            return None
    else:
        try:
            response = client.invoke(ResolveUsernameRequest(channame))
            channel_id = response.peer.channel_id
        except Exception as e:
            print(str(e))
            return None

    if signals_limit > 0:
        for msg in client.get_messages(channel_id, limit=signals_limit):
            if (msg != '') and not (msg is None):
                if not hasattr(msg, 'text'):
                    msg.text = None
                if msg.text is None and hasattr(msg, 'entities'):
                    msg.text = markdown.unparse(msg.message, msg.entities or [])
                if msg.text is None and msg.message:
                    msg.text = msg.message
                separateCheck(msg.text, chantitle, dateFix(msg.date), client)

    return client.get_input_entity(channel_id)

def getConfigValue(config, value):
    try:
        result = config[value]
    except:
        result = ''
    return result

def fixRegex(txt):
    global signal_coin2_variants
    if txt is not None and txt and signal_coin2_variants:
        txt = txt.replace('__COIN2_VARIANTS__', signal_coin2_variants)
    return txt

def loadChanDataFromConfig(config):
    channels = []
    signal_expr = []

    chancount = int(config['channels']['count'])
    default_price1_regex = str(config['channels']['signal_default_price1_regex'])
    default_price2_regex = str(config['channels']['signal_default_price2_regex'])
    default_sell_regex = str(config['channels']['signal_default_target_regex'])
    default_stoploss_regex = str(config['channels']['signal_default_stoploss_regex'])
    default_rating = int(config['channels']['signal_default_rating'])

    chan_index = 1
    cname = ''
    cid = 0
    last_cname = ''
    last_cid = 0
    while chan_index <= chancount:
        channel_config = config['channel_' + str(chan_index)]
        expr_b = RegBlock()
        expr_b.reg_has = fixRegex(getConfigValue(channel_config, 'signal_has'))
        expr_b.reg_skip = fixRegex(getConfigValue(channel_config, 'signal_skip'))
        expr_b.reg_coin = fixRegex(getConfigValue(channel_config, 'signal_coin'))
        expr_b.reg_coin2 = fixRegex(getConfigValue(channel_config, 'signal_coin2'))
        expr_b.reg_price_from = fixRegex(getConfigValue(channel_config, 'signal_price_from'))
        expr_b.reg_price_to = fixRegex(getConfigValue(channel_config, 'signal_price_to'))
        expr_b.reg_price_target = fixRegex(getConfigValue(channel_config, 'signal_price_target'))
        expr_b.reg_price_stoploss = fixRegex(getConfigValue(channel_config, 'signal_stoploss'))
        if not expr_b.reg_has:
            expr_b.reg_has = expr_b.reg_coin
        if expr_b.reg_has:
            if not expr_b.reg_price_from:
                expr_b.reg_price_from = default_price1_regex
            if not expr_b.reg_price_to:
                expr_b.reg_price_to = default_price2_regex
            if not expr_b.reg_price_target:
                expr_b.reg_price_target = default_sell_regex
            if not expr_b.reg_price_stoploss:
                expr_b.reg_price_stoploss = default_stoploss_regex
            try:
                expr_b.rating = int(channel_config['signal_rating'])
            except:
                expr_b.rating = default_rating
        if cname or cid != 0:
            last_cname = cname
            last_cid = cid

        try:
            cid = int(channel_config['id'])
        except:
            cid = 0
        cname = getConfigValue(channel_config, 'name')

        if cname or cid != 0:
            newchan = ChannelBlock()
            newchan.name = cname
            newchan.id = cid
            newchan.title = channel_config['title']
            channels.append(newchan)
            expr_b.name = cname
            expr_b.cid = cid
        else:
            expr_b.name = last_cname
            expr_b.cid = last_cid

        signal_expr.append(expr_b)
        chan_index = chan_index + 1
        
    return channels, signal_expr
    
def main():
    if not sys.argv[1] or not sys.argv[2] or not sys.argv[3]:
        return
    enable_win_unicode_console()

    config = configparser.RawConfigParser(allow_no_value = True)
    config.read('signals_listener.ini')
    session_name = config['main']['session_fname']
    api_id = sys.argv[1]
    api_hash = sys.argv[2]
    phone = sys.argv[3]
    try:
        signals_preload = sys.argv[4]
        if signals_preload is not None and signals_preload != '':
            signals_preload = int(signals_preload)
        else:
            signals_preload = -1
    except:
        signals_preload = -1

    global signal_expr
    global signal_separator
    global signal_coin2_variants
    global log

    if signals_preload < 0:
        signals_preload = int(config['channels']['signals_preload_default'])
    signal_separator = str(config['channels']['signal_separator'])
    signal_coin2_variants = str(config['channels']['signal_coin2_variants'])
    debug_mode = int(config['main']['debug_mode']) > 0
    log_file_name = str(config['main']['log_file'])
    log_run_file_name = str(config['main']['log_run_file'])

    proxy_use = int(config['main']['use_proxy'])
    proxy_type = str(config['main']['proxy_type'])
    proxy_host = str(config['main']['proxy_host'])
    proxy_port = str(config['main']['proxy_port'])
    if proxy_port:
        proxy_port = int(proxy_port)

    if debug_mode:
        log = io.open('./' + log_file_name,'w+',encoding='utf8')
        log.write("Starting log:\n")
    else:
        log = None

    channels, signal_expr = loadChanDataFromConfig(config)

    if proxy_use > 0 and proxy_host and proxy_port > 0:
        if not proxy_type:
            proxy_type = 'SOCKS5'
        print('Using '+proxy_type+' proxy: ' + proxy_host + ':' + str(proxy_port))
        if proxy_type == 'SOCKS4':
            proxy_type = socks.SOCKS4
        elif proxy_type == 'HTTP':
            proxy_type = socks.HTTP
        else:
            proxy_type = socks.SOCKS5
        proxy = (proxy_type, proxy_host, proxy_port)
    else:
        proxy = None

    client = TelegramClient(
        session_name,
        api_id=api_id,
        api_hash=api_hash,
        connection_mode=ConnectionMode.TCP_ABRIDGED,
        proxy=proxy,
        update_workers=1,
        spawn_read_thread=True
    )

    print('Connecting to Telegram servers...')
    try:
        if not client.connect():
            print('Initial connection failed. Retrying...')
            if not client.connect():
                print('Could not connect to Telegram servers.')
                return
    except:
        print('Could not connect to Telegram servers.')
        return

    if not client.is_user_authorized():
        client.send_code_request(phone)
        print('Enter the code ('+phone+'): ')
        code = input()
        client.sign_in(phone, code)

    print(client.session.server_address)   # Successfull

    chan_index = 0
    while chan_index < len(channels):
        print(channels[chan_index].title)
        channels[chan_index].entity = checkCurchan(channels[chan_index], client, signals_preload)
        if channels[chan_index].entity:
            @client.on(events.NewMessage(chats=[channels[chan_index].entity], incoming=True))
            def normal_handler(event):
                if log:
                    log.write(">>>\n")
                    log.write(str(event))
                    log.write("<<<\n")
                separateCheck(event.text, getChannelTitleForEvent(channels, event), dateFix(event.message.date), client)
        time.sleep(1)
        chan_index = chan_index + 1

    if log:
        chan_index = 0
        while chan_index < len(channels):
            log.write("\n")
            log.write(str(channels[chan_index].entity))
            chan_index = chan_index + 1
        log.close()
        log = None
        log = io.open('./' + log_run_file_name,'w+',encoding='utf8')
        log.write("Runtime log:\n")

    print('------------------')

    while True:
        time.sleep(0.1)

if __name__ == '__main__':
    main()
